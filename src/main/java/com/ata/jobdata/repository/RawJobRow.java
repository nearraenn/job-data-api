package com.ata.jobdata.repository;

import com.ata.jobdata.model.JobRecord;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The survey file as it actually is: every column a string, keyed by its spreadsheet header.
 * Kept separate from {@link JobRecord} so the messy source format never leaks into the API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RawJobRow(
        @JsonProperty("Timestamp") String timestamp,
        @JsonProperty("Employer") String employer,
        @JsonProperty("Location") String location,
        @JsonProperty("Job Title") String jobTitle,
        @JsonProperty("Years at Employer") String yearsAtEmployer,
        @JsonProperty("Years of Experience") String yearsOfExperience,
        @JsonProperty("Salary") String salary,
        @JsonProperty("Signing Bonus") String signingBonus,
        @JsonProperty("Annual Bonus") String annualBonus,
        @JsonProperty("Annual Stock Value/Bonus") String annualStockValue,
        @JsonProperty("Gender") String gender,
        @JsonProperty("Additional Comments") String additionalComments) {

    /**
     * Years are typed as freely as salary is: {@code "18"}, {@code "1 of employment"}, {@code "1.5"},
     * {@code "<1"}, {@code "-16"} (a sentinel for "no answer", not a real value), {@code ""}.
     */
    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** Single-letter fields accept the source's unpadded month, day and hour ({@code 9/7/2016 5:14:48}). */
    private static final DateTimeFormatter SOURCE_TIMESTAMP = DateTimeFormatter.ofPattern("M/d/yyyy H:m:s");
    /** Fixed width on purpose — a padded ISO string is what makes string ordering chronological. */
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** A row is worth keeping only if it says something about a job. */
    boolean isEmpty() {
        return isBlank(jobTitle) && isBlank(salary) && isBlank(gender) && isBlank(employer) && isBlank(location);
    }

    JobRecord toJobRecord(int id) {
        SalaryParser.Money money = SalaryParser.parse(salary);
        return new JobRecord(
                id,
                parseTimestamp(timestamp),
                blankToNull(timestamp),
                blankToNull(employer),
                blankToNull(location),
                blankToNull(jobTitle),
                parseYears(yearsAtEmployer),
                blankToNull(yearsAtEmployer),
                parseYears(yearsOfExperience),
                blankToNull(yearsOfExperience),
                money.amount(),
                money.currency(),
                blankToNull(salary),
                blankToNull(signingBonus),
                blankToNull(annualBonus),
                blankToNull(annualStockValue),
                blankToNull(gender),
                blankToNull(additionalComments));
    }

    /**
     * The survey writes US-style {@code M/D/YYYY H:MM:SS} — every one of the 3,777 rows, no variants.
     * That form sorts alphabetically by month, so {@code 1/10/2017} would come before {@code 3/21/2016}
     * and the year barely counts at all; re-emitting it as fixed-width ISO-8601 makes lexicographic
     * order chronological, which fixes sorting and makes range filters like
     * {@code ?timestamp[gte]=2017-01-01} work without any date-aware comparison logic.
     */
    private static String parseTimestamp(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), SOURCE_TIMESTAMP).format(ISO_TIMESTAMP);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Negative values are a "no answer" sentinel in this survey, not a real duration — null, not a sign flip. */
    private static Double parseYears(String value) {
        if (isBlank(value)) {
            return null;
        }
        Matcher m = FIRST_NUMBER.matcher(value);
        if (!m.find()) {
            return null;
        }
        double years = Double.parseDouble(m.group());
        return years < 0 ? null : years;
    }

    /** Empty strings become nulls so "missing" is one concept everywhere: in sorting, filtering and JSON. */
    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
