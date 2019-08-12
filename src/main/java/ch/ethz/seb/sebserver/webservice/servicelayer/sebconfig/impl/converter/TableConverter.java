/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.sebconfig.impl.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.model.sebconfig.AttributeType;
import ch.ethz.seb.sebserver.gbl.model.sebconfig.ConfigurationAttribute;
import ch.ethz.seb.sebserver.gbl.model.sebconfig.ConfigurationValue;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Utils;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ConfigurationAttributeDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ConfigurationValueDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.FilterMap;
import ch.ethz.seb.sebserver.webservice.servicelayer.sebconfig.AttributeValueConverter;
import ch.ethz.seb.sebserver.webservice.servicelayer.sebconfig.AttributeValueConverterService;

@Lazy
@Component
@WebServiceProfile
public class TableConverter implements AttributeValueConverter {

    public static final Set<AttributeType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    AttributeType.TABLE,
                    AttributeType.INLINE_TABLE,
                    AttributeType.COMPOSITE_TABLE)));

    private static final String XML_KEY_TEMPLATE = "<key>%s</key>";
    private static final byte[] XML_ARRAY_START = Utils.toByteArray("<array>");
    private static final byte[] XML_ARRAY_END = Utils.toByteArray("</array>");
    private static final byte[] XML_DICT_START = Utils.toByteArray("<dict>");
    private static final byte[] XML_DICT_END = Utils.toByteArray("</dict>");
    private static final byte[] XML_EMPTY_ARRAY = Utils.toByteArray("<array />");

    private static final String JSON_KEY_TEMPLATE = "\"%s\":";
    private static final byte[] JSON_ARRAY_START = Utils.toByteArray("[");
    private static final byte[] JSON_ARRAY_END = Utils.toByteArray("]");
    private static final byte[] JSON_DICT_START = Utils.toByteArray("{");
    private static final byte[] JSON_DICT_END = Utils.toByteArray("}");
    private static final byte[] JSON_EMPTY_ARRAY = Utils.toByteArray("[]");

    private final ConfigurationAttributeDAO configurationAttributeDAO;
    private final ConfigurationValueDAO configurationValueDAO;
    private AttributeValueConverterService attributeValueConverterService;

    public TableConverter(
            final ConfigurationAttributeDAO configurationAttributeDAO,
            final ConfigurationValueDAO configurationValueDAO) {

        this.configurationAttributeDAO = configurationAttributeDAO;
        this.configurationValueDAO = configurationValueDAO;
    }

    @Override
    public void init(final AttributeValueConverterService attributeValueConverterService) {
        this.attributeValueConverterService = attributeValueConverterService;
    }

    @Override
    public Set<AttributeType> types() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void convertToXML(
            final OutputStream out,
            final ConfigurationAttribute attribute,
            final Function<ConfigurationAttribute, ConfigurationValue> valueSupplier) throws IOException {

        convert(out, attribute, valueSupplier.apply(attribute), true);
    }

    @Override
    public void convertToJSON(
            final OutputStream out,
            final ConfigurationAttribute attribute,
            final Function<ConfigurationAttribute, ConfigurationValue> valueSupplier) throws IOException {

        convert(out, attribute, valueSupplier.apply(attribute), false);
    }

    private void convert(
            final OutputStream out,
            final ConfigurationAttribute attribute,
            final ConfigurationValue value,
            final boolean xml) throws IOException {

        final List<List<ConfigurationValue>> values = this.configurationValueDAO.getOrderedTableValues(
                value.institutionId,
                value.configurationId,
                attribute.id)
                .getOrThrow();

        final boolean noValues = CollectionUtils.isEmpty(values);

        if (attribute.type != AttributeType.COMPOSITE_TABLE) {

            out.write(Utils.toByteArray(String.format(
                    (xml) ? XML_KEY_TEMPLATE : JSON_KEY_TEMPLATE,
                    extractName(attribute))));

            if (noValues) {
                out.write((xml) ? XML_EMPTY_ARRAY : JSON_EMPTY_ARRAY);
                out.flush();
                return;
            } else {
                out.write((xml) ? XML_ARRAY_START : JSON_ARRAY_START);
            }
        } else {
            if (noValues) {
                return;
            } else {
                out.write(Utils.toByteArray(String.format(
                        (xml) ? XML_KEY_TEMPLATE : JSON_KEY_TEMPLATE,
                        extractName(attribute))));
            }
        }

        writeRows(
                out,
                getSortedChildAttributes(attribute),
                values,
                this.attributeValueConverterService,
                xml);

        if (attribute.type != AttributeType.COMPOSITE_TABLE) {
            out.write((xml) ? XML_ARRAY_END : JSON_ARRAY_END);
        }

        out.flush();
    }

    private void writeRows(
            final OutputStream out,
            final Map<Long, ConfigurationAttribute> attributeMap,
            final List<List<ConfigurationValue>> values,
            final AttributeValueConverterService attributeValueConverterService,
            final boolean xml) throws IOException {

        final Iterator<List<ConfigurationValue>> irows = values.iterator();

        while (irows.hasNext()) {
            final List<ConfigurationValue> rowValues = irows.next();
            out.write((xml) ? XML_DICT_START : JSON_DICT_START);

            final Iterator<ConfigurationValue> ivalue = rowValues.iterator();

            while (ivalue.hasNext()) {
                final ConfigurationValue value = ivalue.next();
                final ConfigurationAttribute attr = attributeMap.get(value.attributeId);
                final AttributeValueConverter converter =
                        attributeValueConverterService.getAttributeValueConverter(attr);

                if (xml) {
                    converter.convertToXML(out, attr, a -> value);
                } else {
                    converter.convertToJSON(out, attr, a -> value);
                }

                if (!xml && ivalue.hasNext()) {
                    out.write(Utils.toByteArray(Constants.LIST_SEPARATOR));
                }
            }
            out.write((xml) ? XML_DICT_END : JSON_DICT_END);

            if (!xml && irows.hasNext()) {
                out.write(Utils.toByteArray(Constants.LIST_SEPARATOR));
            }

            out.flush();
        }
    }

    private Map<Long, ConfigurationAttribute> getSortedChildAttributes(final ConfigurationAttribute attribute) {
        return this.configurationAttributeDAO
                .allMatching(new FilterMap().putIfAbsent(
                        ConfigurationAttribute.FILTER_ATTR_PARENT_ID,
                        attribute.getModelId()))
                .getOrThrow()
                .stream()
                .sorted()
                .collect(Collectors.toMap(
                        attr -> attr.id,
                        Function.identity()));
    }

}