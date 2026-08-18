package com.ata.jobdata.data;

import com.ata.jobdata.domain.JobRecord;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /** Years are typed as freely as salary is: "18", "1 of employment", "<1", "". */
    private static final Pattern FIRST_INTEGER = Pattern.compile("\\d+");

    /** A row is worth keeping only if it says something about a job. */
    boolean isEmpty() {
        return isBlank(jobTitle) && isBlank(salary) && isBlank(gender) && isBlank(employer) && isBlank(location);
    }

    JobRecord toJobRecord(int id) {
        SalaryParser.Money money = SalaryParser.parse(salary);
        return new JobRecord(
                id,
                blankToNull(timestamp),
                blankToNull(employer),
                blankToNull(location),
                blankToNull(jobTitle),
                firstInteger(yearsAtEmployer),
                firstInteger(yearsOfExperience),
                money.amount(),
                money.currency(),
                blankToNull(salary),
                blankToNull(signingBonus),
                blankToNull(annualBonus),
                blankToNull(annualStockValue),
                blankToNull(gender),
                blankToNull(additionalComments));
    }

    private static Integer firstInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        Matcher m = FIRST_INTEGER.matcher(value);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    /** Empty strings become nulls so "missing" is one concept everywhere: in sorting, filtering and JSON. */
    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
