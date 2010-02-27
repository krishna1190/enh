/*
 * Copyright (C) 2010 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jessies.icsbot;

import android.app.*;
import android.content.*;
import android.database.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.format.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class IcsBotViewActivity extends Activity {
    private static final String TAG = "IcsBot";
    
    private Spinner mCalendarsSpinner;
    
    private ICalendar.Component mCalendar;
    
    private static class CalendarInfo {
        public final long id;
        public final String name;
        
        public CalendarInfo(long id, String name) {
            this.id = id;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    private String getIntentDataAsString() {
        Intent intent = getIntent();
        String result = intent.getStringExtra("ics");
        if (result != null) {
            return result;
        }
        
        Uri content = intent.getData();
        if (content != null) {
            return getUriDataAsString(content);
        }
        return null;
    }
    
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // TODO: show a UI with the calendar-choice spinner if the user has more than one writable calendar?
        
        // This activity is only started when someone asks for an app to VIEW an ics file.
        String data = getIntentDataAsString();
        if (data == null) {
            toastAndLog("No calendar data found");
            finish();
            return;
        }
        System.err.println(data);
        populateCalendarSpinner();
        parseCalendar(data);
        finish();
    }
    
    private void toastAndLog(String message, Throwable th) {
        Toast.makeText(this, message + ": " + th.getMessage(), Toast.LENGTH_SHORT).show();
        Log.w(TAG, message, th);
    }
    
    private void toastAndLog(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.w(TAG, message);
    }
    
    private String getUriDataAsString(Uri content) {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(content);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = is.read(buf)) != -1) {
                baos.write(buf, 0, bytesRead);
            }
            return new String(baos.toByteArray(), "UTF-8");
        } catch (FileNotFoundException fnfe) {
            toastAndLog("Couldn't open data", fnfe);
        } catch (Exception ex) {
            toastAndLog("Couldn't read data", ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    // We don't care enough about this to annoy the user with it.
                    Log.w(TAG, "Could not close InputStream.", ioe);
                }
            }
        }
        return null;
    }
    
    private void parseCalendar(String data) {
        try {
            mCalendar = ICalendar.parseCalendar(data);
        } catch (ICalendar.FormatException fe) {
            toastAndLog("Couldn't parse calendar data", fe);
            return;
        }
        
        int eventCount = 0;
        int importedEventCount = 0;
        Map<String, TimeZone> timeZones = new HashMap<String, TimeZone>();
        if (mCalendar.getComponents() != null) {
            for (ICalendar.Component component : mCalendar.getComponents()) {
                if (component.getName().equals(ICalendar.Component.VTIMEZONE)) {
                    parseTimeZone(component, timeZones);
                } else if (component.getName().equals(ICalendar.Component.VEVENT)) {
                    ++eventCount;
                    if (insertVEvent(component, timeZones)) {
                        ++importedEventCount;
                    }
                }
            }
        }
        if (eventCount == 0) {
            toastAndLog("No events found in calendar data");
        }
        if (importedEventCount != eventCount) {
            toastAndLog("Not all events were added");
        }
    }
    
    // Secrets we shouldn't know about Calendar's database schema...
    private static final String DISPLAY_NAME_COLUMN = "displayName";
    private static final String _ID_COLUMN = "_id";
    private static final String SELECTED_COLUMN = "selected";
    private static final String ACCESS_LEVEL_COLUMN = "access_level";
    private static final int CONTRIBUTOR_ACCESS = 500;
    private static final int STATUS_CONFIRMED = 1;
    // Constants from Calendar.EventsColumns...
    private static final String ALL_DAY = "allDay";
    private static final String CALENDAR_ID = "calendar_id";
    private static final String DESCRIPTION = "description";
    private static final String DTEND = "dtend";
    private static final String DTSTART = "dtstart";
    private static final String DURATION = "duration";
    private static final String EVENT_LOCATION = "eventLocation";
    private static final String EVENT_TIMEZONE = "eventTimezone";
    private static final String STATUS = "eventStatus";
    private static final String TITLE = "title";
    // Intent extra keys, from Calendar...
    private static final String EVENT_BEGIN_TIME = "beginTime";
    private static final String EVENT_END_TIME = "endTime";
    
    private static final String[] AUTHORITIES = new String[] { "calendar", "com.android.calendar" };
    private String mAuthority;
    
    private void populateCalendarSpinner() {
        mCalendarsSpinner = (Spinner) findViewById(R.id.calendars);
        
        String[] columns = new String[] { _ID_COLUMN, DISPLAY_NAME_COLUMN, SELECTED_COLUMN, ACCESS_LEVEL_COLUMN };
        String query = SELECTED_COLUMN + "=1 AND " + ACCESS_LEVEL_COLUMN + ">=" + CONTRIBUTOR_ACCESS;
        // When Calendar was unbundled for Froyo, the authority changed. To support old and new builds, we need to try both.
        Cursor c = null;
        for (String authority : AUTHORITIES) {
            Uri uri = Uri.parse(String.format("content://%s/calendars", authority));
            c = getContentResolver().query(uri, columns, query, null, null /* sort order */);
            if (c != null) {
                mAuthority = authority;
                break;
            }
        }
        
        ArrayList<CalendarInfo> items = new ArrayList<CalendarInfo>();
        try {
            // TODO: write a custom adapter that wraps the cursor?
            int idColumn = c.getColumnIndex(_ID_COLUMN);
            int nameColumn = c.getColumnIndex(DISPLAY_NAME_COLUMN);
            while (c.moveToNext()) {
                long id = c.getLong(idColumn);
                String name = c.getString(nameColumn);
                items.add(new CalendarInfo(id, name));
            }
        } finally {
            c.deactivate();
        }
        
        ArrayAdapter<CalendarInfo> adapter = new ArrayAdapter<CalendarInfo>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCalendarsSpinner.setAdapter(adapter);
    }
    
    private static String extractValue(ICalendar.Component component, String propertyName) {
        ICalendar.Property property = component.getFirstProperty(propertyName);
        return (property != null) ? unescape(property.getValue()) : null;
    }
    
    private static String unescape(String s) {
        if (s.indexOf('\\') == -1) {
            return s;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            if (ch == '\\' && i < s.length() - 1) {
                char ch2 = s.charAt(++i);
                if (ch2 == 'n') {
                    ch2 = '\n';
                }
                result.append(ch2);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }
    
    /**
     * The VEVENT uses the TZID parameter of the DTSTART/DTEND properties to declare the time zone of the time they represent.
     * (Though, if you're lucky, the generating code is sane and uses UTC and doesn't need a TZID.)
     * We need to translate that to a UTC offset.
     * Stupidly, the time zone ids aren't the well-known America/Los_Angeles style; they're arbitrary strings.
     * We need to have parsed VTIMEZONE properties into our timeZones map, and now we need to pull them back out.
     * 
     * This is broken because we don't know whether we need the STANDARD or DAYLIGHT variant, and the TimeZone in the map is always equivalent to STANDARD.
     */
    private String timeZoneIdFromProperty(ICalendar.Property property, Map<String, TimeZone> timeZones) {
        ICalendar.Parameter tzIdParam = property.getFirstParameter("TZID");
        if (tzIdParam == null || tzIdParam.value == null) {
            return "GMT";
        }
        // FIXME: shouldn't ICalendar automatically remove the quotes for us?
        String bogusTzId = tzIdParam.value.replaceFirst("^\"(.*)\"$", "$1");
        TimeZone tz = timeZones.get(bogusTzId);
        if (tz == null) {
            toastAndLog("Can't parse TZID '" + tzIdParam + "'");
        }
        System.err.println("TZID=" + bogusTzId + " " + tz);
        return tz.getID();
    }
    
    private boolean parseTime(String what, ICalendar.Property property, Time time, Map<String, TimeZone> timeZones) {
        String tzId = timeZoneIdFromProperty(property, timeZones);
        time.clear(tzId);
        try {
            String t = property.getValue();
            if (t.endsWith("Z")) {
                // UTC times are easy. Google Calendar wisely uses these, but nothing else seems to.
                time.parse(t);
            } else {
                // Damn it! We need to take the time zone into account.
                // The easiest way I found -- given that we don't have an appropriate TimeZone object anyway --
                // is to rewrite the time string so it's an RFC 3339 timestamp including a UTC offset.
                String tzSuffix = tzId.replaceAll("GMT(.*)", "$1");
                // FIXME: the .000 works around a bug in the crappy android.text.format.Time code.
                t = t.replaceAll("(....)(..)(..)T(..)(..)(..)", "$1-$2-$3T$4:$5:$6.000" + tzSuffix);
                time.parse3339(t);
            }
            return true;
        } catch (Exception e) {
            toastAndLog("Can't parse " + what + " '" + property.getValue() + "'", e);
            return false;
        }
    }
    
    private boolean insertVEvent(ICalendar.Component event, Map<String, TimeZone> timeZones) {
        ContentValues values = new ContentValues();
        
        // title
        String title = extractValue(event, "SUMMARY");
        if (TextUtils.isEmpty(title)) {
            toastAndLog("Can't import an untitled event");
            return false;
        }
        values.put(TITLE, title);
        
        // status
        values.put(STATUS, STATUS_CONFIRMED);
        
        // description
        String description = extractValue(event, "DESCRIPTION");
        if (!TextUtils.isEmpty(description)) {
            values.put(DESCRIPTION, description);
        }
        
        // where
        String where = extractValue(event, "LOCATION");
        if (!TextUtils.isEmpty(where)) {
            values.put(EVENT_LOCATION, where);
        }
        
        // Which calendar should we insert into?
        CalendarInfo calInfo = (CalendarInfo) mCalendarsSpinner.getSelectedItem();
        long calendarId = calInfo.id;
        values.put(CALENDAR_ID, calendarId);
        
        // dtStart & dtEnd
        Time time = new Time(Time.TIMEZONE_UTC);
        long dtStartMillis = 0;
        long dtEndMillis = 0;
        String dtStart = null;
        String dtEnd = null;
        String duration = null;
        ICalendar.Property dtStartProp = event.getFirstProperty("DTSTART");
        if (dtStartProp != null) {
            dtStart = dtStartProp.getValue();
            if (!TextUtils.isEmpty(dtStart)) {
                if (!parseTime("start time", dtStartProp, time, timeZones)) {
                    return false;
                }
                if (time.allDay) {
                    values.put(ALL_DAY, 1);
                }
                dtStartMillis = time.toMillis(false /* use isDst */);
                values.put(DTSTART, dtStartMillis);
                values.put(EVENT_TIMEZONE, time.timezone);
            }
            
            ICalendar.Property dtEndProp = event.getFirstProperty("DTEND");
            if (dtEndProp != null) {
                dtEnd = dtEndProp.getValue();
                if (!TextUtils.isEmpty(dtEnd)) {
                    if (!parseTime("end time", dtEndProp, time, timeZones)) {
                        return false;
                    }
                    dtEndMillis = time.toMillis(false /* use isDst */);
                    values.put(DTEND, dtEndMillis);
                }
            } else {
                // look for a duration
                ICalendar.Property durationProp = event.getFirstProperty("DURATION");
                if (durationProp != null) {
                    duration = durationProp.getValue();
                    if (!TextUtils.isEmpty(duration)) {
                        // TODO: check that it is valid?
                        values.put(DURATION, duration);
                    }
                }
            }
        }
        
        if (TextUtils.isEmpty(dtStart)) {
            toastAndLog("Can't import events that don't have a start time");
            return false;
        }
        if (TextUtils.isEmpty(dtEnd) && TextUtils.isEmpty(duration)) {
            toastAndLog("Can't import events that have neither an end time nor a duration");
            return false;
        }
        
        // rrule
        /*
        if (!RecurrenceSet.populateContentValues(event, values)) {
            return null;
        }
        */
        
        // FIXME: use the VALARM data to set a reminder.
        
        // Insert the event...
        Uri uri = getContentResolver().insert(Uri.parse(String.format("content://%s/events", mAuthority)), values);
        if (uri == null) {
            toastAndLog("Couldn't import event '" + title + "'");
            return false;
        }
        
        // ...and immediately ask Calendar to open up the event-editing UI.
        // (Ideally, we'd be able to go to the new-event UI, but this is as close as we can get.)
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setData(uri);
        // Is this a Calendar bug? It never looks at the begin/end times actually stored in the event!
        // If we don't resupply them like this, they get overwritten with 0.
        intent.putExtra(EVENT_BEGIN_TIME, dtStartMillis);
        intent.putExtra(EVENT_END_TIME, dtEndMillis);
        startActivity(intent);
        return true;
    }
    
    /**
     * Translates VTIMEZONE components into entries in the String to TimeZone map.
     * The entry corresponds to the STANDARD variant, and we ignore the DAYLIGHT variant.
     * This is obviously broken, but it's better than the previous behavior (which was to always assume UTC).
     * 
     * It's not obvious to me how to implement the right behavior.
     * I don't think we can use SimpleTimeZone because I don't think it can represent the complicated rules like "2nd Sunday".
     * 
     * Google Calendar gets round this by taking the eminently sane attitude that all times should be UTC.
     * It lets the sending and receiving users' settings determine how the event appears.
     */
    private void parseTimeZone(ICalendar.Component timeZone, Map<String, TimeZone> timeZones) {
        String name = extractValue(timeZone, "TZID");
        int standardDay = 0, standardDayOfWeek = 0, standardMonth = 0, daylightDay = 0, daylightDayOfWeek = 0, daylightMonth = 0;
        int standardOffset = 0, daylightOffset = 0;
        for (ICalendar.Component variant : timeZone.getComponents()) {
            // RRULE:FREQ=YEARLY;BYMINUTE=0;BYHOUR=2;BYDAY=1SU;BYMONTH=11
            boolean standard = variant.getName().equals("STANDARD");
            ICalendar.Property rule = variant.getFirstProperty("RRULE");
            if (rule == null) {
                throw new IllegalArgumentException(name + ", variant " + variant.getName() + " has no RRULE!");
            }
            for (String param : rule.getValue().split(";")) {
                String[] keyValue = param.split("=");
                String key = keyValue[0];
                String value = keyValue[1];
                if (key.equals("FREQ")) {
                    if (!value.equals("YEARLY")) {
                        throw new IllegalArgumentException(rule.getValue() + ": FREQ not YEARLY!");
                    }
                } else if (key.equals("BYDAY")) {
                    Matcher m = Pattern.compile("(-?\\d+)(..)").matcher(value);
                    if (!m.matches()) {
                        throw new IllegalArgumentException(rule.getValue() + ": can't parse BYDAY!");
                    }
                    if (standard) {
                        standardDay = Integer.parseInt(matcher.group(1));
                        standardDayOfWeek = parseDayName(matcher.group(2));
                    } else {
                        daylightDay = Integer.parseInt(matcher.group(1));
                        daylightDayOfWeek = parseDayName(matcher.group(2));
                    }
                } else if (key.equals("BYMONTH")) {
                    if (standard) {
                        standardMonth = Integer.parseInt(value);
                    } else {
                        daylightMonth = Integer.parseInt(value);
                    }
                } else {
                    System.err.println("warning: ignoring key " + key + " (value " + value + ") for " + name + ", variant " + variant.getName());
                }
            }
            
            ICalendar.Property offset = variant.getFirstProperty("TZOFFSETTO");
            if (offset != null && offset.getValue() != null) {
                int rawOffset = parseUtcOffset(offset.getValue());
                if (standard) {
                    standardOffset = rawOffset;
                } else {
                    daylightOffset = rawOffset;
                }
            } else {
                System.err.println("didn't understand TZOFFSETTO in '" + name + "' variant '" + variant.getName() + "'");
            }
        }
        SimpleTimeZone tz = new SimpleTimeZone(standardOffset, name, daylightMonth, daylightDay, daylightDayOfWeek, 7200000, standardMonth, standardDay, standardDayOfWeek, 7200000, Math.abs(daylightOffset - standardOffset));
        timeZones.put(name, tz);
        System.err.println("added '" + name + "' as " + tz);
    }
    
    /**
     * Parses an RFC 5545 UTC offset and returns milliseconds.
     * 
     * Grammar:
     *        utc-offset = time-numzone
     *        time-numzone = ("+" / "-") time-hour time-minute [time-second]
     * 
     * Examples:
     * -0500
     * +0100
     */
    private int parseUtcOffset(String value) {
        final Pattern utcOffsetPattern = Pattern.compile("([-+])(\\d{2})(\\d{2})(?:\\d{2})?");
        final Matcher matcher = utcOffsetPattern.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(value);
        }
        int sign = matcher.group(1).equals("+") ? 1 : -1;
        int hours = Integer.parseInt(matcher.group(2));
        int minutes = Integer.parseInt(matcher.group(3));
        int offsetSeconds = sign * (hours*60 + minutes)*60;
        return offsetSeconds * 1000;
    }
    
    private int parseDayName(String twoLetterDayName) {
        if (twoLetterDayName.equals("SU")) {
            return Calendar.SUNDAY;
        } else if (twoLetterDayName.equals("MO")) {
            return Calendar.MONDAY;
        } else if (twoLetterDayName.equals("TU")) {
            return Calendar.TUESDAY;
        } else if (twoLetterDayName.equals("WE")) {
            return Calendar.WEDNESDAY;
        } else if (twoLetterDayName.equals("TH")) {
            return Calendar.THURSDAY;
        } else if (twoLetterDayName.equals("FR")) {
            return Calendar.FRIDAY;
        } else if (twoLetterDayName.equals("SA")) {
            return Calendar.SATURDAY;
        } else {
            throw new IllegalArgumentException(twoLetterDayName);
        }
    }
}
