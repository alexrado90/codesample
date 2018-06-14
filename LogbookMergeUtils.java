package ca.truxtrax.logbook;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.crashlytics.android.Crashlytics;
import com.truxtrax.utils.CollectionUtils;
import com.truxtrax.utils.DatabaseUtils;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.truxtrax.activities.logbook.LogbookUtils;
import ca.truxtrax.database.realm_dao.BaseDAO;
import ca.truxtrax.database.realm_dao.EldAnnotationsDao;
import ca.truxtrax.database.realm_dao.eld.EldDutyEventsDao;
import ca.truxtrax.database.realm_dao.eld.EldEventsDao;
import ca.truxtrax.database.realm_mapping.eld.EldAnnotation;
import ca.truxtrax.database.realm_mapping.eld.EldEvent;
import ca.truxtrax.server.ServerOperations;
import ca.truxtrax.services.eld_events.EldEventsUtils;
import ca.truxtrax.settings.AppSettings;
import ca.truxtrax.settings.SettingsEditor;
import ca.truxtrax.utils.AndroidLog;
import ca.truxtrax.utils.CalendarUtils;
import ca.truxtrax.utils.EldUtils;
import ca.truxtrax.utils.LocationUtils;
import ca.truxtrax.utils.MapperUtils;
import io.realm.Realm;
import server.mapping_socket.eld_mapping.EldEventItem;
import server.mapping_socket.eld_mapping.EldRemoveEventItem;

import static ca.truxtrax.logbook.LogbookMergeUtils.EventsMerger.combineEquals;

/**
 * Created by alexa on 13.01.2018.
 */

public class LogbookMergeUtils {

    private static final String tag = LogbookMergeUtils.class.getSimpleName();

    public synchronized static List<EventMergeWrapper> merge(final Realm realm,
                                                             final long user,
                                                             final List<EldEvent> events,
                                                             final EldEvent mergeEvent,
                                                             final long rightDate,
                                                             final long theTime,
                                                             final String annotationStr) {

        if (!LogbookUtils.isValidUser(user)) {
            AndroidLog.e(tag, "not valid user, user==" + user);
            return null;
        }

        if (!DatabaseUtils.valid(realm)) {
            AndroidLog.e(tag, "realm not valid");
            return null;
        }

        if (!EldEventsUtils.isValidEvent(mergeEvent)) {
            AndroidLog.e(tag, "event not valid");
            return null;
        }

        if (!isValidDutyEvent(mergeEvent)) {
            return null;
        }

        if (CalendarUtils.timeEquals(mergeEvent.getDatetime(), rightDate)) {
            AndroidLog.e(tag, "mergeEvent.datetime==" + rightDate + ", " + rightDate);
            return null;
        }

        if (!CalendarUtils.isTimeNotContainsSecondsAndMillis(rightDate)) {
            AndroidLog.e(tag, "not valid rightDate==" + rightDate);
            return null;
        }

        if (!CalendarUtils.isTimeNotContainsSecondsAndMillis(theTime)) {
            AndroidLog.e(tag, "not valid theTime==" + theTime);
            return null;
        }
        if (theTime < rightDate) {
            AndroidLog.e(tag, "theTime < rightDate, ("
                    + CalendarUtils.getFormattedDate(new DateTime(theTime), CalendarUtils.FORMATT_eee_mmm_dd_hh_mm)
                    + " < "
                    + CalendarUtils.getFormattedDate(new DateTime(rightDate), CalendarUtils.FORMATT_eee_mmm_dd_hh_mm)
                    + ")"
            );
            return null;
        }

        if (CollectionUtils.isEmpty(events)) {
            return null;
        }

        return merge0(realm, user, events, mergeEvent, rightDate, theTime, annotationStr);
    }

    private static List<EventMergeWrapper> merge0(final Realm realm,
                                                  final long user,
                                                  final List<EldEvent> events,
                                                  final EldEvent mergeEvent,
                                                  final long rightDate,
                                                  final long theTime,
                                                  final String annotationStr) {

        final List<EventMergeWrapper> result = new ArrayList<>();

        try {
            DatabaseUtils.executeTransaction(realm, new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {

                    EldEvent[] coveredEventResult = new EldEvent[1];
                    boolean shouldMerge = checkNeedToMerge(coveredEventResult, events, mergeEvent, rightDate);
                    List<EventMergeWrapper> mergeResult = null;
                    if (shouldMerge) {
                        mergeResult = mergeEvents(events, mergeEvent, rightDate, theTime);
                    } else {
                        if (coveredEventResult[0] != null) {
                            // merge to covered status
                            mergeResult = new ArrayList<>();
                            LogbookMergeUtils.EventMergeWrapper.editEvent(mergeResult, coveredEventResult[0], mergeEvent);
                        }
                    }

                    if (!CollectionUtils.isEmpty(mergeResult)) {

                        // save and push events
                        saveAndPush(realm, user, mergeResult);

                        // save and push annotation
                        if (!TextUtils.isEmpty(annotationStr) && annotationStr.trim().length() >= EldAnnotation.MIN_COMMENT_LENGTH) {
                            EldAnnotation annotation = mergeAnnotation(new EldAnnotationsDao(realm), mergeResult, mergeEvent.getDatetime(), annotationStr);
                            if (annotation != null) {
                                saveAndPushAnnotation(realm, user, annotation);
                            }
                        }

                        // reset certification
                        //TODO

                        // copy result from transaction
                        result.addAll(mergeResult);
                    }

                }
            });
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Crashlytics.logException(e);
            return result;
        }

        return result;
    }

    private static EldAnnotation mergeAnnotation(EldAnnotationsDao dao, List<EventMergeWrapper> result, long time, String annotationStr) {

        EldEvent eventToAnnotate = findEventToAnnotate(result, time);
        if (eventToAnnotate == null) {
            return null;
        }

        EldAnnotation annotation = dao.getAnnotation(eventToAnnotate.getId());
        if (annotation == null) {
            return new EldAnnotation(BaseDAO.generateUuid(), eventToAnnotate.getUser(), time, eventToAnnotate.getId(), annotationStr);
        } else {
            annotation.setComment(annotationStr);
            return annotation;
        }
    }

    private static EldEvent findEventToAnnotate(List<EventMergeWrapper> result, long time) {
        // finding appropriate event
        // event with equal date or nearest left event
        EldEvent theEvent = null;
        for (int i = 0; i < result.size(); i++) {
            EventMergeWrapper it = result.get(i);
            switch (it.mergeResult) {
                case EventMergeWrapper.MERGE_RESULT_EDITED:
                case EventMergeWrapper.MERGE_RESULT_NEW:

                    // assign by default
                    if (theEvent == null) {
                        if (it.event.getDatetime() <= time) {
                            theEvent = it.event;
                        }
                    } else if (it.event.getDatetime() == time) {

                        // event found
                        return it.event;
                    } else if (it.event.getDatetime() > theEvent.getDatetime()
                            && it.event.getDatetime() < time) {
                        theEvent = it.event;
                    }
                    break;
            }
        }
        return theEvent;
    }

    private static void saveAndPushAnnotation(Realm realm, Long user, EldAnnotation annotation) {
        pushAnnotationServer(realm, user, annotation);
        saveAnnotationToDatabase(realm, annotation);
    }

    private static void pushAnnotationServer(Realm realm, Long user, EldAnnotation annotation) {
        if (annotation.getEvent().startsWith(EldEvent.EVENT_STUB_CLIENT_ID)) {
            return;
        }
        ServerOperations.saveAnnotation(realm, user, annotation);
    }

    private static void saveAnnotationToDatabase(Realm realm, EldAnnotation annotation) {
        EldAnnotationsDao annotationsDao = new EldAnnotationsDao(realm);
        annotationsDao.copyToRealmOrUpdate(annotation);
    }

    private static void saveAndPush(Realm realm, Long user, List<EventMergeWrapper> mergeEvent) {
        pushMergeResultToServer(realm, user, mergeEvent);
        saveMergeResultToDatabase(realm, mergeEvent);
    }

    private static void pushMergeResultToServer(Realm realm, Long user, List<EventMergeWrapper> mergeEvent) {
        final List<EldEventItem> mergeList = new ArrayList<>();
        final List<EldRemoveEventItem> removeList = new ArrayList<>();

        for (EventMergeWrapper it : mergeEvent) {

            // should not be sent to server, only for local display
            if (it.event.getId().startsWith(EldEvent.EVENT_STUB_CLIENT_ID)) {
                continue;
            }

            switch (it.mergeResult) {
                case EventMergeWrapper.MERGE_RESULT_REMOVED:
                    removeList.add(new EldRemoveEventItem(it.event.getId()));
                    break;
                case EventMergeWrapper.MERGE_RESULT_NEW:
                case EventMergeWrapper.MERGE_RESULT_EDITED:
                case EventMergeWrapper.MERGE_RESULT_REJECTED:
                    mergeList.add(MapperUtils.convertToServerEldEvent(it.event));
                    break;

            }
        }

        if (!CollectionUtils.isEmpty(mergeList) || !CollectionUtils.isEmpty(removeList)) {
            ServerOperations.mergeEvents(realm, user, mergeList, removeList);
        }
    }

    private static void saveMergeResultToDatabase(Realm realm, List<EventMergeWrapper> mergeEvent) {

        EldEventsDao dao = new EldEventsDao(realm);
        for (EventMergeWrapper it : mergeEvent) {
            switch (it.mergeResult) {
                case EventMergeWrapper.MERGE_RESULT_REMOVED:
                    dao.delete(it.event);
                    break;
                case EventMergeWrapper.MERGE_RESULT_NEW:
                    dao.copyToRealm(it.event);
                    break;
                case EventMergeWrapper.MERGE_RESULT_EDITED:
                case EventMergeWrapper.MERGE_RESULT_REJECTED:
                    dao.copyToRealmOrUpdate(it.event);
                    break;
            }
        }
    }

    private static List<EventMergeWrapper> mergeEvents(List<EldEvent> events, EldEvent mergeEvent, long rightDate, long theTime) {
        EventsMerger merger = new EventsMerger(events, mergeEvent, rightDate, theTime);
        return merger.execute();
    }

    public static List<EventMergeWrapper> mergeLastEvent(Context context, long user, int logbookStatus) {
        Float engineHours = null;
        Double odometer = null;
        if (EldUtils.isConfigured(context)) {
            float engineHoursCache = EldUtils.getCachedEngineHoursFloat(context);
            engineHours = engineHoursCache == SettingsEditor.FLOAT_NULL_VALUE ? null : engineHoursCache;
            double cachedOdometer = EldUtils.getCachedOdometerFloat(context);
            odometer = (cachedOdometer == SettingsEditor.FLOAT_NULL_VALUE ? null : cachedOdometer);
        }

        if (odometer == null) {
            odometer = 0d;
        }

        if (engineHours == null) {
            engineHours = 0f;
        }

        Location location = LocationUtils.lastKnownLocation(context);
        String locationStr = location == null ? null : LocationUtils.getNearestCityDirectionFormattedString(context, location);
        final Long eldId = EldUtils.getConnectedEldId(context);

        int logbookIntervalInMins = new AppSettings(context).getLogbookFrequencyInMins();
        DateTime currentTime = LogbookCalendar.getInstance(logbookIntervalInMins);
        long left = currentTime.getMillis();
        long right = currentTime.getMillis() + TimeUnit.MINUTES.toMillis(logbookIntervalInMins);

        EldEvent mergeEvent = new EldEvent(BaseDAO.generateUuid(),
                user,
                left,
                EldEvent.ORIGIN_DRIVER,
                locationStr,
                odometer,
                engineHours
        );
        mergeEvent.setMilesOriginal(odometer);
        mergeEvent.setHoursOriginal(engineHours);
        mergeEvent.setEld(eldId);
        mergeEvent.setLogbookStatus(logbookStatus);

        if (location == null) {
            mergeEvent.setLatLon(EldEvent.LOCATION_X);
        } else {
            mergeEvent.setLat((float) location.getLatitude());
            mergeEvent.setLon((float) location.getLongitude());
        }
        long theTime = currentTime.getMillis() + TimeUnit.MINUTES.toMillis(new AppSettings(context).getLogbookFrequencyInMins());

        Realm realm = Realm.getDefaultInstance();
        try {
            return LogbookMergeUtils.merge(realm, user, new EldDutyEventsDao(realm).selectForDriverId(user), mergeEvent, right, theTime, null);
        } finally {
            DatabaseUtils.closeQuietly(realm);
        }
    }

    public static class EventsMerger {
        final List<EldEvent> events;
        final EldEvent mergeEvent;
        final long rightDate;
        final Long theTime;

        public EventsMerger(List<EldEvent> events, EldEvent mergeEvent, long rightDate, long theTime) {
            this.events = events;
            this.mergeEvent = mergeEvent;
            this.rightDate = rightDate;
            this.theTime = theTime;
        }

        public List<EventMergeWrapper> execute() {
            List<EventMergeWrapper> resultsList = new ArrayList<>();

            boolean leftFound = false, rightFound = false;

            for (int i = 0; i < events.size(); i++) {

                EldEvent event = events.get(i);
                // remove all between the draggers
                if (event.getDatetime() > mergeEvent.getDatetime() && event.getDatetime() < rightDate) {
                    EventMergeWrapper.removeEvent(resultsList, event);
                }
                // if left dates equals, merge them
                else if (event.getDatetime() == mergeEvent.getDatetime()) {

                    leftFound = true;
                    EventMergeWrapper.mergeTwoEvents(resultsList, event, mergeEvent);
                } else if (event.getDatetime() == rightDate) {
                    rightFound = true;
                }
            }

            if (!leftFound) {
                EldEvent nearestLeftEvent = findNearestLeft(events, mergeEvent.getDatetime(), false);
                if (nearestLeftEvent != null) {
                    if (equalsEventsByTypeCode(nearestLeftEvent, mergeEvent)) {
                        EventMergeWrapper.editEvent(resultsList, nearestLeftEvent, mergeEvent);
                    } else {
                        EventMergeWrapper.closeIfNeedDrivingEvent(resultsList, nearestLeftEvent, mergeEvent);
                        EventMergeWrapper.newEvent(resultsList, mergeEvent);
                    }
                }
            }

            if (!rightFound) {
                // nearest left event from rightDate
                if (rightDate != theTime) {
                    EldEvent eventCopy = findNearestLeft(events, rightDate, true);
                    if (eventCopy != null) {
                        if (!equalsEventsByTypeCode(eventCopy, mergeEvent)) {
                            EventMergeWrapper.newEvent(resultsList, eventCopy);
                        }
                    }
                }
            }

            combineEquals(resultsList, events);

            return resultsList;
        }

        protected static void combineEquals(List<EventMergeWrapper> result, List<EldEvent> events) {

            for (int i = 0; i < events.size(); i++) {

                EldEvent it = events.get(i);

                if (findMergeResultStatus(it, result).mergeResult == EventMergeWrapper.MERGE_RESULT_REMOVED) {
                    continue;
                }

                for (int y = i + 1; y < events.size(); y++) {

                    EldEvent nextIt = events.get(y);
                    // ignore removed event
                    EventMergeWrapper mergeResult = findMergeResultStatus(nextIt, result);
                    if (mergeResult.mergeResult == EventMergeWrapper.MERGE_RESULT_REMOVED) {
                        continue;
                    }

                    if (canBeMergedInOne(it, nextIt)) {
                        switch (mergeResult.mergeResult) {
                            case EventMergeWrapper.MERGE_RESULT_NEW:
                                // this event will be ignored
                                EventMergeWrapper.cancelEventResult(result, mergeResult);
                                break;
                            default:
                                EventMergeWrapper.changeEventResult(result, mergeResult, EventMergeWrapper.MERGE_RESULT_REMOVED);
                                break;
                        }

                        i = y + 1;
                        continue;
                    }
                    break;
                }
            }
        }

        private static EventMergeWrapper findMergeResultStatus(EldEvent event, List<EventMergeWrapper> result) {
            for (EventMergeWrapper it : result) {
                if (it.event == event) {
                    return it;
                }
            }
            return new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NO_CHANGES, event);
        }

        /**
         * find nearest left to {@date} event, copy it, assign date
         */
        private EldEvent findNearestLeft(List<EldEvent> events, long date, boolean copy) {
            for (int i = events.size() - 1; i >= 0; i--) {
                EldEvent it = events.get(i);
                if (it.getDatetime() < date) {
                    if (copy) {
                        EldEvent eventCopy = it.copy();
                        eventCopy.setId(BaseDAO.generateUuid());
                        eventCopy.setDatetime(date);
                        return eventCopy;
                    }
                    return it;
                }
            }
            return null;
        }
    }

    public static final class EventMergeWrapper {
        public static final int MERGE_RESULT_NO_CHANGES = 0;
        public static final int MERGE_RESULT_EDITED = 1;
        public static final int MERGE_RESULT_REMOVED = 2;
        public static final int MERGE_RESULT_NEW = 3;
        public static final int MERGE_RESULT_CLOSE_DRIVE = 4;
        public static final int MERGE_RESULT_REJECTED = 5;

        public final int mergeResult;
        public final EldEvent event;

        public EventMergeWrapper(int mergeResult, EldEvent event) {
            this.mergeResult = mergeResult;
            this.event = event;
        }

        static void mergeTwoEvents(List<EventMergeWrapper> resultsList, EldEvent event1, EldEvent event2) {

            if (event1.getType().intValue() == event2.getType().intValue()) {
                // copy only certain field
                copySignificantFields(event1, event2);
                resultsList.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_EDITED, event1));
                // TODO annotations
            } else {
                // remove event, and replace by "merge candidate"
                EldEvent copy = event1.copy();
                copy.setId(event2.getId());
                copySignificantFields(copy, event2);
                resultsList.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_REMOVED, event1));
                resultsList.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NEW, copy));
            }
        }

        private static void copySignificantFields(EldEvent to, EldEvent from) {
            to.setLogbookStatus(from.getLogbookStatus());
            to.setLocation(from.getLocation());
            to.setHours(from.getHours());
            to.setMiles(from.getMiles());
        }

        static void removeEvent(List<EventMergeWrapper> result, EldEvent event) {
            result.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_REMOVED, event));
        }

        static void editEvent(List<EventMergeWrapper> result, EldEvent event, EldEvent mergeEvent) {
            copySignificantFields(event, mergeEvent);
            result.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_EDITED, event));
        }

        static void newEvent(List<EventMergeWrapper> result, EldEvent mergeEvent) {
            result.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_NEW, mergeEvent));
        }

        public static void rejectEvent(List<EventMergeWrapper> result, EldEvent event) {
            result.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_REJECTED, event));
        }

        static void changeEventResult(List<EventMergeWrapper> result, EventMergeWrapper resultEvent, int mergeStatus) {
            result.remove(resultEvent);

            result.add(new EventMergeWrapper(mergeStatus, resultEvent.event));
        }

        static void cancelEventResult(List<EventMergeWrapper> result, EventMergeWrapper resultEvent) {
            result.remove(resultEvent);
        }

        static void closeIfNeedDrivingEvent(List<EventMergeWrapper> resultsList, EldEvent event, EldEvent mergeEvent) {
            if (event.getLogbookStatus() == EldEvent.STATUS_DRIVING && event.getOrigin() == EldEvent.ORIGIN_AUTO) {
                boolean closed = EventUtils.isDrivingClosed(event);
                if (!closed) {
                    // duration
                    int duration = (int) (TimeUnit.MILLISECONDS.toMinutes(mergeEvent.getDatetime() - event.getDatetime()));
                    event.setDuration(duration >= 0 ? duration : 0);

                    // accumulated hours
                    float hoursOriginal = event.getHoursOriginal() == null ? 0F : event.getHoursOriginal();
                    float hours = mergeEvent.getHoursOriginal() == null ? 0F : mergeEvent.getHoursOriginal();
                    float accumulatedHours = hours - hoursOriginal;
                    event.setHoursAccumulated(accumulatedHours >= 0f ? accumulatedHours : 0f);

                    // accumulated miles
                    double milesOriginal = event.getMilesOriginal() == null ? 0D : event.getMilesOriginal();
                    double miles = mergeEvent.getMilesOriginal() == null ? 0D : mergeEvent.getMilesOriginal();
                    double accumulatedMiles = miles - milesOriginal;
                    event.setMilesAccumulated(accumulatedMiles >= 0d ? accumulatedMiles : 0d);

                    resultsList.add(new EventMergeWrapper(EventMergeWrapper.MERGE_RESULT_CLOSE_DRIVE, event));
                }
            }
        }
    }

    private static boolean isValidDutyEvent(EldEvent event) {

        if (!EldEventsUtils.isValidEvent(event)) {
            return false;
        }

        if (event.getType() != EldEvent.TYPE_DUTY_STATUS && event.getType() != EldEvent.TYPE_YM_PC) {
            AndroidLog.e(tag, "wrong event.type==" + event.getType());
            return false;
        }
        return true;
    }

    private static boolean checkNeedToMerge(EldEvent[] coveredEventResult, List<EldEvent> allEvents, EldEvent mergeCandidate, long rightDate) throws IllegalStateException {
        for (int i = 0; i < allEvents.size(); i++) {

            EldEvent st = allEvents.get(i);
            EldEvent nextSt = null;
            if (i != allEvents.size() - 1) {
                nextSt = allEvents.get(i + 1);
            }

            boolean covered;
            boolean coveredPart;

            if (nextSt == null) {
                // covered by last existing event
                covered = st.getDatetime() <= mergeCandidate.getDatetime();
            } else {
                // covered by event
                covered = st.getDatetime() <= mergeCandidate.getDatetime() && rightDate <= nextSt.getDatetime();
            }

            if (covered) {

                // check if the status can be split or changed in duration
                boolean res = coveredEventCanBeEdited(st);
                if (!res) {
                    coveredEventResult[0] = st;
                    return false;
                }

                // need to create new status
                if (!equalsEventsByTypeCode(st, mergeCandidate)) {
                    return true;
                }

                coveredEventResult[0] = st;
                return false;
            } else {

                // partially covered by events
                if (nextSt == null) {
                    boolean mergeCandidateCoverAnotherEvent = st.getDatetime() > mergeCandidate.getDatetime();
                    if (mergeCandidateCoverAnotherEvent) {
                        boolean res = coveredEventCanBeEdited(st);
                        if (!res) {
                            return false;
                        }
                    }
                } else {
                    coveredPart = st.getDatetime() < mergeCandidate.getDatetime()
                            && nextSt.getDatetime() > mergeCandidate.getDatetime()
                            && rightDate > nextSt.getDatetime();
                    boolean mergeCandidateCoverAnotherEvent = st.getDatetime() >= mergeCandidate.getDatetime() && rightDate >= nextSt.getDatetime();
                    if (coveredPart || mergeCandidateCoverAnotherEvent) {
                        boolean res = coveredEventCanBeEdited(st);
                        if (!res) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean coveredEventCanBeEdited(EldEvent from) throws IllegalStateException {

        // driving auto can't be changed to any other type/code
        boolean caseDriveOriginAuto = from.getOrigin() == EldEvent.ORIGIN_AUTO
                && from.getLogbookStatus() == EldEvent.STATUS_DRIVING;

        if (caseDriveOriginAuto) {
            if (EventUtils.isDrivingClosed(from)) {
//                throw new IllegalStateException("Can't be edited(cut), event==" + from.toString());
                return false;
            }
        }
        return true;
    }

    public static boolean canBeMergedInOne(EldEvent e1, EldEvent e2) {
        if (e1.getLogbookStatus() == e2.getLogbookStatus()) {

            boolean caseDriveOriginAutoE1 = e1.getOrigin() == EldEvent.ORIGIN_AUTO && e1.getLogbookStatus() == EldEvent.STATUS_DRIVING;
            boolean caseDriveOriginAutoE2 = e2.getOrigin() == EldEvent.ORIGIN_AUTO && e2.getLogbookStatus() == EldEvent.STATUS_DRIVING;
            if (caseDriveOriginAutoE1 || caseDriveOriginAutoE2) {
                return false;
            }

            return true;
        }
        return false;
    }

    public static boolean equalsEventsByTypeCode(EldEvent e1, EldEvent e2) {
        return e1.getLogbookStatus() == e2.getLogbookStatus();
    }

    public static List<EventMergeWrapper> rejectEvent(Realm realm, long user, List<EldEvent> events, EldEvent event) {

        if (!DatabaseUtils.valid(realm)) {
            AndroidLog.e(tag, "realm is invalid");
            return null;
        }

        if (!LogbookUtils.isValidUser(user)) {
            AndroidLog.e(tag, "not valid user, user==" + user);
            return null;
        }

        if (CollectionUtils.isEmpty(events)) {
            AndroidLog.e(tag, "events list is empty or null");
            return null;
        }

        if (event == null) {
            AndroidLog.e(tag, "event==null");
            return null;
        }

        if (!EldEventsUtils.isValidEvent(event)) {
            AndroidLog.e(tag, "event is invalid, " + event.toString());
            return null;
        }

        if (event.getLogbookStatus() != EldEvent.STATUS_DRIVING) {
            AndroidLog.e(tag, "wrong logbookStatus, expected(" + EldEvent.STATUS_DRIVING + "), logbookStatus==" + event.getLogbookStatus());
            return null;
        }

        if (event.getOrigin() != EldEvent.ORIGIN_AUTO) {
            AndroidLog.e(tag, "wrong origin, expected(" + EldEvent.ORIGIN_AUTO + "), origin==" + event.getOrigin());
            return null;
        }

        if (!EventUtils.isDrivingClosed(event)) {
            AndroidLog.e(tag, "driving event not closed" + event.getOrigin());
            return null;
        }

        // FIXME stub
        if (event.getMilesAccumulated() == null) {
            event.setMilesAccumulated(0D);
        }
        if (event.getHoursAccumulated() == null) {
            event.setHoursAccumulated(0F);
        }

        return rejectEvent0(realm, user, events, event);
    }

    private static List<EventMergeWrapper> rejectEvent0(final Realm realm, final long user, final List<EldEvent> events, final EldEvent event) {

        final List<EventMergeWrapper> result = new ArrayList<>();

        // make event undefined
        DatabaseUtils.executeTransaction(realm, new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                // clear user and set to unidentified origin
                event.setUser(null);
                event.setOrigin(EldEvent.ORIGIN_UNIDENTIFIED);

                EventMergeWrapper.rejectEvent(result, event);

                combineEquals(result, events);

                saveAndPush(realm, user, result);
            }
        });

        return result;
    }
}
