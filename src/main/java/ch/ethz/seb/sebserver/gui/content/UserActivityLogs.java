/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.gui.content;

import org.eclipse.swt.widgets.Composite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.model.Domain;
import ch.ethz.seb.sebserver.gbl.model.user.UserActivityLog;
import ch.ethz.seb.sebserver.gbl.profile.GuiProfile;
import ch.ethz.seb.sebserver.gbl.util.Utils;
import ch.ethz.seb.sebserver.gui.content.action.ActionDefinition;
import ch.ethz.seb.sebserver.gui.form.FormBuilder;
import ch.ethz.seb.sebserver.gui.service.ResourceService;
import ch.ethz.seb.sebserver.gui.service.i18n.I18nSupport;
import ch.ethz.seb.sebserver.gui.service.i18n.LocTextKey;
import ch.ethz.seb.sebserver.gui.service.page.PageContext;
import ch.ethz.seb.sebserver.gui.service.page.PageService;
import ch.ethz.seb.sebserver.gui.service.page.PageService.PageActionBuilder;
import ch.ethz.seb.sebserver.gui.service.page.TemplateComposer;
import ch.ethz.seb.sebserver.gui.service.page.impl.ModalInputDialog;
import ch.ethz.seb.sebserver.gui.service.page.impl.PageAction;
import ch.ethz.seb.sebserver.gui.service.remote.webservice.api.RestService;
import ch.ethz.seb.sebserver.gui.service.remote.webservice.api.userlogs.GetUserLogPage;
import ch.ethz.seb.sebserver.gui.table.ColumnDefinition;
import ch.ethz.seb.sebserver.gui.table.ColumnDefinition.TableFilterAttribute;
import ch.ethz.seb.sebserver.gui.table.EntityTable;
import ch.ethz.seb.sebserver.gui.table.TableFilter.CriteriaType;
import ch.ethz.seb.sebserver.gui.widget.WidgetFactory;

@Lazy
@Component
@GuiProfile
public class UserActivityLogs implements TemplateComposer {

    private static final LocTextKey DETAILS_TITLE_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.details.title");
    private static final LocTextKey TITLE_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.title");
    private static final LocTextKey EMPTY_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.empty");
    private static final LocTextKey USER_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.column.user");
    private static final LocTextKey DATE_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.column.dateTime");
    private static final LocTextKey ACTIVITY_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.column.activityType");
    private static final LocTextKey ENTITY_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.column.entityType");
    private static final LocTextKey MESSAGE_TEXT_KEY =
            new LocTextKey("sebserver.userlogs.list.column.message");
    private final static LocTextKey EMPTY_SELECTION_TEXT =
            new LocTextKey("sebserver.userlogs.info.pleaseSelect");

    private final TableFilterAttribute userFilter;
    private final TableFilterAttribute activityFilter;
    private final TableFilterAttribute entityFilter;

    private final PageService pageService;
    private final ResourceService resourceService;
    private final I18nSupport i18nSupport;
    private final WidgetFactory widgetFactory;
    private final int pageSize;

    public UserActivityLogs(
            final PageService pageService,
            final ResourceService resourceService,
            @Value("${sebserver.gui.list.page.size:20}") final Integer pageSize) {

        this.pageService = pageService;
        this.resourceService = resourceService;
        this.i18nSupport = resourceService.getI18nSupport();
        this.widgetFactory = pageService.getWidgetFactory();
        this.pageSize = pageSize;

        this.userFilter = new TableFilterAttribute(
                CriteriaType.SINGLE_SELECTION,
                UserActivityLog.FILTER_ATTR_USER,
                this.resourceService::userResources);

        this.activityFilter = new TableFilterAttribute(
                CriteriaType.SINGLE_SELECTION,
                UserActivityLog.FILTER_ATTR_ACTIVITY_TYPES,
                this.resourceService::userActivityTypeResources);

        this.entityFilter = new TableFilterAttribute(
                CriteriaType.SINGLE_SELECTION,
                UserActivityLog.FILTER_ATTR_ENTITY_TYPES,
                this.resourceService::entityTypeResources);
    }

    @Override
    public void compose(final PageContext pageContext) {
        final WidgetFactory widgetFactory = this.pageService.getWidgetFactory();
        final RestService restService = this.resourceService.getRestService();
        // content page layout with title
        final Composite content = widgetFactory.defaultPageLayout(
                pageContext.getParent(),
                TITLE_TEXT_KEY);

        final PageActionBuilder actionBuilder = this.pageService.pageActionBuilder(
                pageContext
                        .clearEntityKeys()
                        .clearAttributes());

        // table
        final EntityTable<UserActivityLog> table = this.pageService.entityTableBuilder(
                restService.getRestCall(GetUserLogPage.class))
                .withEmptyMessage(EMPTY_TEXT_KEY)
                .withPaging(this.pageSize)

                .withColumn(new ColumnDefinition<>(
                        UserActivityLog.ATTR_USER_NAME,
                        USER_TEXT_KEY,
                        UserActivityLog::getUsername)
                                .withFilter(this.userFilter))

                .withColumn(new ColumnDefinition<UserActivityLog>(
                        Domain.USER_ACTIVITY_LOG.ATTR_ACTIVITY_TYPE,
                        ACTIVITY_TEXT_KEY,
                        this.resourceService::getUserActivityTypeName)
                                .withFilter(this.activityFilter)
                                .sortable())

                .withColumn(new ColumnDefinition<UserActivityLog>(
                        Domain.USER_ACTIVITY_LOG.ATTR_ENTITY_ID,
                        ENTITY_TEXT_KEY,
                        this.resourceService::getEntityTypeName)
                                .withFilter(this.entityFilter)
                                .sortable())

                .withColumn(new ColumnDefinition<>(
                        Domain.USER_ACTIVITY_LOG.ATTR_TIMESTAMP,
                        DATE_TEXT_KEY,
                        this::getLogTime)
                                .withFilter(new TableFilterAttribute(
                                        CriteriaType.DATE_RANGE,
                                        UserActivityLog.FILTER_ATTR_FROM_TO,
                                        Utils.toDateTimeUTC(Utils.getMillisecondsNow())
                                                .minusYears(1)
                                                .toString()))
                                .sortable())

                .withDefaultAction(t -> actionBuilder
                        .newAction(ActionDefinition.LOGS_USER_ACTIVITY_SHOW_DETAILS)
                        .withExec(action -> this.showDetails(action, t.getSelectedROWData()))
                        .noEventPropagation()
                        .create())

                .compose(content);

        actionBuilder
                .newAction(ActionDefinition.LOGS_USER_ACTIVITY_SHOW_DETAILS)
                .withSelect(
                        table::getSelection,
                        action -> this.showDetails(action, table.getSelectedROWData()),
                        EMPTY_SELECTION_TEXT)
                .noEventPropagation()
                .publishIf(table::hasAnyContent);

    }

    private final String getLogTime(final UserActivityLog log) {
        if (log == null || log.timestamp == null) {
            return Constants.EMPTY_NOTE;
        }

        return this.i18nSupport
                .formatDisplayDateTime(Utils.toDateTimeUTC(log.timestamp));
    }

    private PageAction showDetails(final PageAction action, final UserActivityLog userActivityLog) {
        action.getSingleSelection();

        final ModalInputDialog<Void> dialog = new ModalInputDialog<>(
                action.pageContext().getParent().getShell(),
                this.widgetFactory);

        dialog.open(
                DETAILS_TITLE_TEXT_KEY,
                action.pageContext(),
                pc -> createDetailsForm(userActivityLog, pc));

        return action;
    }

    private void createDetailsForm(final UserActivityLog userActivityLog, final PageContext pc) {
        this.pageService.formBuilder(pc, 3)
                .withEmptyCellSeparation(false)
                .readonly(true)
                .addField(FormBuilder.text(
                        UserActivityLog.ATTR_USER_NAME,
                        USER_TEXT_KEY,
                        String.valueOf(userActivityLog.getUsername())))
                .addField(FormBuilder.text(
                        Domain.USER_ACTIVITY_LOG.ATTR_ACTIVITY_TYPE,
                        ACTIVITY_TEXT_KEY,
                        this.resourceService.getUserActivityTypeName(userActivityLog)))
                .addField(FormBuilder.text(
                        Domain.USER_ACTIVITY_LOG.ATTR_ENTITY_ID,
                        ENTITY_TEXT_KEY,
                        this.resourceService.getEntityTypeName(userActivityLog)))
                .addField(FormBuilder.text(
                        Domain.USER_ACTIVITY_LOG.ATTR_TIMESTAMP,
                        DATE_TEXT_KEY,
                        this.widgetFactory.getI18nSupport()
                                .formatDisplayDateTime(Utils.toDateTimeUTC(userActivityLog.timestamp))))
                .addField(FormBuilder.text(
                        Domain.USER_ACTIVITY_LOG.ATTR_MESSAGE,
                        MESSAGE_TEXT_KEY,
                        String.valueOf(userActivityLog.message).replace(",", ",\n"))
                        .asArea())
                .build();
        this.widgetFactory.labelSeparator(pc.getParent());
    }

}
