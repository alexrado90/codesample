package ca.truxtrax.test.activiries.logbook;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.internal.WhiteboxImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.truxtrax.database.realm_mapping.eld.EldEvent;
import ca.truxtrax.logbook.LogbookMergeUtils;
import utils.BaseRealmRunner;

import static ca.truxtrax.logbook.LogbookMergeUtils.EventMergeWrapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static utils.Utils.generateEvent;

/**
 * Created by alexa on 16.01.2018.
 */

@PrepareForTest({LogbookMergeUtils.class, LogbookMergeUtils.EventMergeWrapper.class})
public class EventMergeWrapperTest extends BaseRealmRunner {

    @Test
    public void shouldRemoveEventFromResults() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2);
        List<EventMergeWrapper> results = new ArrayList<>(Arrays.asList(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NEW, event)));

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "removeEvent", results, event);

        //then
        assertFalse(results.isEmpty());
    }

    @Test
    public void shouldAddEventEditResults() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2);
        EldEvent mergeEvent = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 3);

        List<EventMergeWrapper> results = new ArrayList<>();

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "editEvent", results, event, mergeEvent);

        // then
        // added one event
        assertEquals("results size", 1, results.size());
        EventMergeWrapper resultEvent = results.get(0);
        // same events
        assertEquals(event, resultEvent.event);
        assertEquals("merge status", EventMergeWrapper.MERGE_RESULT_EDITED, resultEvent.mergeResult);
    }

    @Test
    public void shouldAddEventToResults() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2);
        List<EventMergeWrapper> results = new ArrayList<>();

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "newEvent", results, event);

        // then
        // added one event
        assertEquals("results size", 1, results.size());
        EventMergeWrapper resultEvent = results.get(0);
        // same events
        assertEquals(event, resultEvent.event);
        assertEquals("merge status", EventMergeWrapper.MERGE_RESULT_NEW, resultEvent.mergeResult);
    }

    @Test
    public void shouldChangeEventResult() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2);
        EventMergeWrapper eventRes = new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NEW, event);
        List<EventMergeWrapper> results = new ArrayList<>(Arrays.asList(eventRes));

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "changeEventResult", results, eventRes, EventMergeWrapper.MERGE_RESULT_EDITED);

        // then
        assertEquals("results size", 1, results.size());
        EventMergeWrapper resultEvent = results.get(0);
        assertEquals(event, resultEvent.event);
        assertEquals("merge status", EventMergeWrapper.MERGE_RESULT_EDITED, resultEvent.mergeResult);
    }

    @Test
    public void shouldCancelEventResult() throws Exception {

        // given
        EventMergeWrapper eventRes = new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NEW, generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2));
        List<EventMergeWrapper> results = new ArrayList<>(Arrays.asList(eventRes));

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "cancelEventResult", results, eventRes);

        // then
        assertEquals("results size", 0, results.size());
    }

    @Test
    public void shouldCloseDrivingEvent() throws Exception {
        // given
        List<EventMergeWrapper> results = new ArrayList<>();
        EldEvent drivingEvent = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);
        drivingEvent.setMilesOriginal(1D);
        drivingEvent.setHoursOriginal(2F);
        EldEvent offDutyEvent = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 25);
        offDutyEvent.setMilesOriginal(3D);
        offDutyEvent.setHoursOriginal(7F);

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "closeIfNeedDrivingEvent", results, drivingEvent, offDutyEvent);

        // then
        assertEquals("results size", 1, results.size());
        assertEquals(20, drivingEvent.getDuration(), 0);
        assertEquals(2d, drivingEvent.getMilesAccumulated(), 0);
        assertEquals(5f, drivingEvent.getHoursAccumulated(), 0);

    }

    @Test
    public void shouldMergeTwoEvents() throws Exception {

        // case #1, with save events
        // given
        EldEvent eventDriving = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1);
        EldEvent eventSleeping = generateEvent(EldEvent.STATUS_SLEEPING, EldEvent.ORIGIN_DRIVER, 1);
        List<EventMergeWrapper> results = new ArrayList<>();

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "mergeTwoEvents", results, eventDriving, eventSleeping);

        // then
        assertEquals("results size", 1, results.size());
        EventMergeWrapper eventRes1 = results.get(0);
        assertEquals(eventRes1.event, eventDriving);
        assertEquals(eventRes1.event.getLogbookStatus(), EldEvent.STATUS_SLEEPING);
        assertEquals(eventRes1.mergeResult, EventMergeWrapper.MERGE_RESULT_EDITED);


        // case #2, different Event.type
        // given
        EldEvent eventOff = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 1);
        EldEvent eventYm = generateEvent(EldEvent.STATUS_YM, EldEvent.ORIGIN_DRIVER, 1);
        List<EventMergeWrapper> results2 = new ArrayList<>();

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "mergeTwoEvents", results2, eventOff, eventYm);

        // then
        assertEquals("results size", 2, results2.size());
        EventMergeWrapper eventRes2 = results2.get(0);
        assertEquals(eventRes2.mergeResult, EventMergeWrapper.MERGE_RESULT_REMOVED);
        assertEquals(eventRes2.event, eventOff);
        EventMergeWrapper eventRes3 = results2.get(1);
        assertEquals(eventRes3.mergeResult, EventMergeWrapper.MERGE_RESULT_NEW);
        assertEquals(eventRes3.event.getLogbookStatus(), EldEvent.STATUS_YM);
    }

    @Test
    public void shouldCopySignificantFields() throws Exception {
        // given
        EldEvent copyTo = generateEvent(EldEvent.STATUS_YM, EldEvent.ORIGIN_DRIVER, 1);
        copyTo.setMiles(12D);
        copyTo.setHours(11F);
        copyTo.setLocation("Moscow");
        EldEvent copyFrom = generateEvent(EldEvent.STATUS_YM, EldEvent.ORIGIN_DRIVER, 1);
        copyFrom.setMiles(9D);
        copyFrom.setHours(8F);
        copyFrom.setLocation("Sevastopol");

        // when
        WhiteboxImpl.invokeMethod(EventMergeWrapper.class, "copySignificantFields", copyTo, copyFrom);

        // then
        assertEquals(9D, copyTo.getMiles(), 0d);
        assertEquals(8f, copyTo.getHours(), 0d);
        assertEquals("Sevastopol", copyTo.getLocation());
        assertEquals(EldEvent.CODE_INDICATES_YARD_MOVES, copyFrom.getCode(), 0);
        assertEquals(EldEvent.TYPE_YM_PC, copyFrom.getType(), 0);
    }
}
