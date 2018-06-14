package ca.truxtrax.test.activiries.logbook;

import org.joda.time.DateTime;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.internal.WhiteboxImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.truxtrax.activities.logbook.LogbookUtils;
import ca.truxtrax.database.realm_dao.BaseDAO;
import ca.truxtrax.database.realm_dao.EldAnnotationsDao;
import ca.truxtrax.database.realm_mapping.eld.EldAnnotation;
import ca.truxtrax.database.realm_mapping.eld.EldEvent;
import ca.truxtrax.logbook.LogbookMergeUtils;
import ca.truxtrax.utils.CalendarUtils;
import io.realm.Realm;
import utils.BaseRealmRunner;

import static ca.truxtrax.logbook.LogbookMergeUtils.EventMergeWrapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static utils.Utils.generateEvent;

/**
 * Created by alexa on 14.01.2018.
 */

@PrepareForTest({LogbookMergeUtils.class, EventMergeWrapper.class, LogbookUtils.class})
public class LogbookMergeUtilsTest extends BaseRealmRunner {

    @Test
    public void shouldValidateMergeFields() throws Exception {

        // given, valid params
        long validUser = 10;
        Realm validRealm = mockRealm;
        when(validRealm.isClosed()).thenReturn(false);
        EldEvent validEvent = new EldEvent(BaseDAO.generateUuid(),
                validUser,
                null,
                CalendarUtils.getDateWithoutSecondsAndMilliseconds(DateTime.now()).getMillis(),
                EldEvent.ORIGIN_DRIVER)
                .setLogbookStatus(EldEvent.CODE_OFF_DUTY);
        validEvent.setLat(33f);
        validEvent.setLon(44f);
        validEvent.setHours(0F);
        validEvent.setMiles(0D);
        Long validRightDate = TimeUnit.DAYS.toMillis(1);
        Long validTheDate = TimeUnit.DAYS.toMillis(2);
        String validAnnotation = null;

        List<EldEvent> validEvents = mock(ArrayList.class);
        when(validEvents.get(0)).thenReturn(new EldEvent());
        when(validEvents.isEmpty()).thenReturn(false);

        PowerMockito.spy(LogbookMergeUtils.class);
        PowerMockito.doReturn(new ArrayList<EventMergeWrapper>())
                .when(LogbookMergeUtils.class, "merge0", eq(mockRealm), anyLong(), anyListOf(EldEvent.class), any(EldEvent.class), anyLong(), anyLong(), anyString());


        // not valid user
        List<EventMergeWrapper> resNonValidUser = LogbookMergeUtils.merge(mockRealm, -10, validEvents, new EldEvent(), TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2), null);
        assertNull(resNonValidUser);

        // not valid realm
        Realm nonValidRealm = PowerMockito.mock(Realm.class);
        when(nonValidRealm.isClosed()).thenReturn(true);
        List<EventMergeWrapper> resNonValidRealm = LogbookMergeUtils.merge(nonValidRealm, validUser, validEvents, new EldEvent(), TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2), null);
        assertNull(resNonValidRealm);

        // not valid event
        List<EventMergeWrapper> resNonValidEvent = LogbookMergeUtils.merge(validRealm, validUser, validEvents, new EldEvent(), TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2), null);
        assertNull(resNonValidEvent);

        // not valid event
        EldEvent notValidEvent = validEvent.copy();
        notValidEvent.setLon(null);
        notValidEvent.setLat(null);
        List<EventMergeWrapper> resNonValidEvent2 = LogbookMergeUtils.merge(validRealm, validUser, validEvents, notValidEvent, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2), null);
        assertNull(resNonValidEvent2);

        // not valid rightDate
        List<EventMergeWrapper> resNonValidRightDate = LogbookMergeUtils.merge(validRealm, validUser, validEvents, validEvent, new DateTime().withSecondOfMinute(10).getMillis(), TimeUnit.DAYS.toMillis(2), null);
        assertNull(resNonValidRightDate);

        // not valid theTime
        List<EventMergeWrapper> resNonValidTheTime = LogbookMergeUtils.merge(validRealm, validUser, validEvents, validEvent, validRightDate, new DateTime().withSecondOfMinute(10).getMillis(), null);
        assertNull(resNonValidTheTime);

        // not valid events
        List<EventMergeWrapper> resNonValidEvents = LogbookMergeUtils.merge(validRealm, validUser, new ArrayList<EldEvent>(), validEvent, validRightDate, new DateTime().withSecondOfMinute(10).getMillis(), null);
        assertNull(resNonValidEvents);

        // valid
        List<EventMergeWrapper> resultSucceed = LogbookMergeUtils.merge(validRealm, validUser, validEvents, validEvent, validRightDate, validTheDate, validAnnotation);
        assertNotNull(resultSucceed);
    }

    @Test
    public void shouldMergeInOneEvent() {

        // dr + dr
        EldEvent eventDr1 = new EldEvent().setLogbookStatus(EldEvent.STATUS_DRIVING);
        eventDr1.setOrigin(EldEvent.ORIGIN_DRIVER);

        EldEvent eventDr2 = new EldEvent().setLogbookStatus(EldEvent.STATUS_DRIVING);
        eventDr2.setOrigin(EldEvent.ORIGIN_DRIVER);

        assertTrue(LogbookMergeUtils.canBeMergedInOne(eventDr1, eventDr2));


        // ym + ym
        EldEvent eventOn1 = new EldEvent().setLogbookStatus(EldEvent.STATUS_YM);
        eventOn1.setOrigin(EldEvent.ORIGIN_DRIVER);

        EldEvent eventOn2 = new EldEvent().setLogbookStatus(EldEvent.STATUS_YM);
        eventOn2.setOrigin(EldEvent.ORIGIN_AUTO);
        assertTrue(LogbookMergeUtils.canBeMergedInOne(eventOn1, eventOn2));
    }

    @Test
    public void shouldNotMergeInOneEvent() {
        EldEvent event1 = new EldEvent().setLogbookStatus(EldEvent.STATUS_DRIVING);
        event1.setOrigin(EldEvent.ORIGIN_AUTO);

        EldEvent event2 = new EldEvent().setLogbookStatus(EldEvent.STATUS_DRIVING);
        event2.setOrigin(EldEvent.ORIGIN_AUTO);

        EldEvent event3 = new EldEvent().setLogbookStatus(EldEvent.CODE_OFF_DUTY);
        event3.setOrigin(EldEvent.ORIGIN_DRIVER);

        EldEvent event4 = new EldEvent().setLogbookStatus(EldEvent.STATUS_ON_DUTY);
        event4.setOrigin(EldEvent.ORIGIN_DRIVER);

        EldEvent event5 = new EldEvent().setLogbookStatus(EldEvent.STATUS_DRIVING);
        event5.setOrigin(EldEvent.ORIGIN_DRIVER);

        //dr.auto && dr.auto
        assertFalse(LogbookMergeUtils.canBeMergedInOne(event1, event2));
        // dr.auto && dr.driver
        assertFalse(LogbookMergeUtils.canBeMergedInOne(event1, event5));
        // dr.driver && dr.auto
        assertFalse(LogbookMergeUtils.canBeMergedInOne(event5, event1));
        // off.driver && on.driver
        assertFalse(LogbookMergeUtils.canBeMergedInOne(event3, event4));
    }

    @Test
    public void shouldAllowSplitEvent() throws Exception {

        // given
        EldEvent event1 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);

        // when
        WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "coveredEventCanBeEdited", event1);

        // not throw exception
    }

    @Test
    public void shouldReturnCoveredEvent() throws Exception {

        //given
        EldEvent event1 = generateEvent(EldEvent.CODE_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);
        EldEvent event3 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2);
        EldEvent[] coveredEventResult = new EldEvent[1];

        // when
        WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                coveredEventResult,
                Arrays.asList(event1, event3),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2),
                TimeUnit.MINUTES.toMillis(3)
        );

        // then
        assertTrue(coveredEventResult[0].equals(event3));
    }

    @Test
    public void shouldMergeEvents() throws Exception {

        EldEvent event1 = generateEvent(EldEvent.CODE_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);
        EldEvent event3 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 3);
        EldEvent event4 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 4);

        // candidate in last right status, not equals left border
        // in [0, ~](off)
        // candidate [1 to 2](dr)
        boolean result = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertTrue(result);

        // candidate in last right status, from left border
        // in [0, ~](off)
        // candidate [0 to 2](dr)
        boolean result2 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 0),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertTrue(result2);

        // candidate between left and right border
        // in [0, 3](off), [3, ~](dr)
        // candidate [1-2](dr)
        boolean result3 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event3),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertTrue(result3);

        // candidate between left(equals) and right border
        // in [0, 3](off), [3, ~](dr)
        // candidate [0-2](dr)
        boolean result4 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event3),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 0),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertTrue(result4);

        // candidate between left(equals) and right border(equals)
        // in [0, 3](off), [3, ~](dr)
        // candidate [0-3](dr)
        boolean result5 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event3),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 0),
                TimeUnit.MINUTES.toMillis(3)
        );
        assertTrue(result5);

        // candidate in left and out of right border
        // in [0, 3](off), [3,~](dr)
        // candidate [1-4](dr)
        boolean result6 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event3),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(4)
        );
        assertTrue(result6);

        // candidate out of left and right border
        // in [0, 3](off), [3,4](dr), [4, ~] off
        // candidate [1-5](off)
        boolean result7 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event3, event4),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(5)
        );
        assertTrue(result7);
    }

    @Test
    public void shouldNotMergeDrivingAuto() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 0);
        event.setDuration(10);

        // when
        boolean result = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event),
                generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(5)
        );

        // then
        assertFalse(result);
    }

    @Test
    public void shouldEditDrivingAutoEvent() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 0);
        event.setDuration(10);
        event.setLocation("Location1");
        event.setMiles(123D);

        EldEvent mergeEvent = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 1);
        mergeEvent.setLocation("Location2");
        mergeEvent.setMiles(321D);

        mockStatic(LogbookMergeUtils.class, CALLS_REAL_METHODS);
        PowerMockito.spy(LogbookMergeUtils.class);

        PowerMockito.doNothing().when(LogbookMergeUtils.class, "saveAndPush", any(Realm.class), anyLong(), anyListOf(EventMergeWrapper.class));

        // when
        List<EventMergeWrapper> result = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "merge0",
                mockRealm,
                123L,
                Arrays.asList(event),
                mergeEvent,
                TimeUnit.MINUTES.toMillis(10),
                TimeUnit.MINUTES.toMillis(10),
                null
        );

        // then
        assertEquals(1, result.size());
        assertEquals("Location2", result.get(0).event.getLocation());
        assertEquals(321D, result.get(0).event.getMiles(), 0.1f);
    }

    @Test
    public void shouldNotAllowSplitEvent() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);
        event.setDuration(10);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "coveredEventCanBeEdited", event);

        // then
        assertFalse(res);
    }

    @Test
    public void shouldAllowSplitNotClosedDrivingEvent() throws Exception {
        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "coveredEventCanBeEdited", event);

        // then no throw
        assertTrue(res);
    }

    @Test
    public void shouldNotMergeDrivingAutoToAnotherEvent() throws Exception {

        // given
        EldEvent event = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 0);
        event.setDuration(10);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(5)
        );

        assertFalse(res);
    }

    @Test
    public void shouldNotSplitDrivingAutoEvent() throws Exception {

        EldEvent event1 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 0);
        event1.setDuration(10);
        // candidate between borders
        // in [0, ~]
        // candidate [1-2](off)
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(2)
        );

        // then
        assertFalse(res);
    }

    @Test
    public void shouldNotMergeDrivingPartiallyLeft() throws Exception {

        // given
        EldEvent event1 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);
        EldEvent event2 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);
        event2.setDuration(10);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event2),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 4),
                TimeUnit.MINUTES.toMillis(6)
        );

        // then
        assertFalse(res);
    }

    @Test
    public void shouldNotMergeDrivingPartiallyRight() throws Exception {

        // given
        EldEvent event1 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 0);
        event1.setDuration(5);
        EldEvent event2 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 5);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event2),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 4),
                TimeUnit.MINUTES.toMillis(6)
        );

        // then
        assertFalse(res);
    }

    @Test
    public void shouldAllowMerge() throws Exception {

        // given
        EldEvent event1 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);
        EldEvent event2 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);
        event2.setDuration(5);
        EldEvent event3 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 10);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event2, event3),
                generateEvent(EldEvent.STATUS_ON_DUTY, EldEvent.ORIGIN_DRIVER, 11),
                TimeUnit.MINUTES.toMillis(15)
        );

        // then
        assertTrue(res);
    }

    //    @Test(expected = IllegalStateException.class)
    public void shouldNotMergeDrivingWholeCovered() throws Exception {

        // given
        EldEvent event1 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);
        EldEvent event2 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_AUTO, 5);
        event2.setDuration(5);
        EldEvent event3 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 10);

        // when
        boolean res = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1, event2, event3),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 3),
                TimeUnit.MINUTES.toMillis(12)
        );

        // then
        assertFalse(res);
    }

    @Test
    public void shouldNotMergeEvents() throws Exception {
        EldEvent event1 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0);

        // candidate between left and right borders
        // in [0, ~](off)
        // candidate [1-2](off)
        boolean result = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(event1),
                generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertFalse(result);

        // candidate between left(equals) and right(equals) borders
        // in [0, 1](off), [1,2](sb), [2,~](dr)
        // candidate [1-2](off)
        boolean result2 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "checkNeedToMerge",
                new EldEvent[1],
                Arrays.asList(
                        generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 0),
                        generateEvent(EldEvent.STATUS_SLEEPING, EldEvent.ORIGIN_DRIVER, 1),
                        generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 2)
                ),
                generateEvent(EldEvent.STATUS_SLEEPING, EldEvent.ORIGIN_DRIVER, 1),
                TimeUnit.MINUTES.toMillis(2)
        );
        assertFalse(result2);
    }

    @Test
    public void shouldFindEventToAnnotate() throws Exception {

        // #1 should not find event
        // given
        EldEvent e1 = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 10);
        EldEvent e2 = generateEvent(EldEvent.STATUS_ON_DUTY, EldEvent.ORIGIN_DRIVER, 20);
        EldEvent e3 = generateEvent(EldEvent.STATUS_SLEEPING, EldEvent.ORIGIN_DRIVER, 30);
        EldEvent e4 = generateEvent(EldEvent.STATUS_DRIVING, EldEvent.ORIGIN_DRIVER, 40);

        LogbookMergeUtils.EventMergeWrapper wr1 = new LogbookMergeUtils.EventMergeWrapper(LogbookMergeUtils.EventMergeWrapper.MERGE_RESULT_EDITED, e1.copy());
        LogbookMergeUtils.EventMergeWrapper wr2 = new LogbookMergeUtils.EventMergeWrapper(LogbookMergeUtils.EventMergeWrapper.MERGE_RESULT_EDITED, e2.copy());
        LogbookMergeUtils.EventMergeWrapper wr3 = new LogbookMergeUtils.EventMergeWrapper(LogbookMergeUtils.EventMergeWrapper.MERGE_RESULT_REMOVED, e3.copy());
        LogbookMergeUtils.EventMergeWrapper wr4 = new LogbookMergeUtils.EventMergeWrapper(LogbookMergeUtils.EventMergeWrapper.MERGE_RESULT_NEW, e4.copy());
        List<LogbookMergeUtils.EventMergeWrapper> list = Arrays.asList(wr1, wr2, wr3, wr4);

        // when
        EldEvent res1 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "findEventToAnnotate", list, TimeUnit.MINUTES.toMillis(5));

        // then
        assertNull(res1);


        // #2 should return event with the same time
        // given
        List<LogbookMergeUtils.EventMergeWrapper> list2 = Arrays.asList(wr1, wr2, wr3, wr4);

        // when
        EldEvent res2 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "findEventToAnnotate", list2, TimeUnit.MINUTES.toMillis(20));

        //then
        assertEquals(e2, res2);


        // #3 should ignore REMOVED event and find nearest left
        // given
        List<LogbookMergeUtils.EventMergeWrapper> list3 = Arrays.asList(wr1, wr2, wr3, wr4);

        // when
        EldEvent res3 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class, "findEventToAnnotate", list3, TimeUnit.MINUTES.toMillis(30));

        //then
        assertEquals(e2, res3);
    }

    @Test
    public void shouldMergeAnnotation() throws Exception {

        // #1 should return null
        // given

        PowerMockito.spy(LogbookMergeUtils.class);
        PowerMockito.doReturn(null).when(LogbookMergeUtils.class, "findEventToAnnotate", anyListOf(LogbookMergeUtils.EventMergeWrapper.class), anyLong());

        // when
        EldAnnotation res1 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class,
                "mergeAnnotation",
                any(EldAnnotationsDao.class),
                anyListOf(LogbookMergeUtils.EventMergeWrapper.class),
                anyLong(),
                anyString());

        // then
        assertNull(res1);


        // #2 should return with replaced annotation
        // given
        EldEvent event = generateEvent(EldEvent.STATUS_OFF_DUTY, EldEvent.ORIGIN_DRIVER, 10);
        event.setUser(10L);
        EldAnnotation annotation = new EldAnnotation(BaseDAO.generateUuid(), 10, TimeUnit.MINUTES.toMillis(10), event.getId(), "first comment");
        EldAnnotationsDao mockDao = mock(EldAnnotationsDao.class);
        when(mockDao.getAnnotation(anyString())).thenReturn(annotation);
        PowerMockito.doReturn(event.copy()).when(LogbookMergeUtils.class, "findEventToAnnotate", anyListOf(LogbookMergeUtils.EventMergeWrapper.class), anyLong());

        // when
        EldAnnotation res2 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class,
                "mergeAnnotation",
                mockDao,
                new ArrayList<>(),
                10L,
                "second comment");

        // then
        // comment changed
        assertEquals("second comment", res2.getComment());
        // return found annotation
        assertEquals(annotation.getId(), res2.getId());


        // #3 should return with new annotation
        // given
        when(mockDao.getAnnotation(anyString())).thenReturn(null);
        PowerMockito.doReturn(event.copy()).when(LogbookMergeUtils.class, "findEventToAnnotate", anyListOf(LogbookMergeUtils.EventMergeWrapper.class), anyLong());

        // when
        EldAnnotation res3 = WhiteboxImpl.invokeMethod(LogbookMergeUtils.class,
                "mergeAnnotation",
                mockDao,
                new ArrayList<>(),
                20L,
                "second comment");

        // then
        // new annotation created
        assertNotNull(res3);
        // comment changed
        assertEquals("second comment", res2.getComment());
    }

//    private static EldAnnotation mergeAnnotation(EldAnnotationsDao dao, List<EventMergeWrapper> result, long time, String annotationStr) {
//
//        EldEvent eventToAnnotate = findEventToAnnotate(result, time);
//        if (eventToAnnotate == null) {
//            return null;
//        }
//
//        EldAnnotation annotation = dao.getAnnotation(eventToAnnotate.getId());
//        if (annotation == null) {
//            return new EldAnnotation(BaseDAO.generateUuid(), eventToAnnotate.getUser(), time, eventToAnnotate.getId(), annotationStr);
//        } else {
//            annotation.setComment(annotationStr);
//            return annotation;
//        }
//    }
}