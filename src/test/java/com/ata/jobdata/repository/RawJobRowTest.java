package com.ata.jobdata.repository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Years at Employer / Years of Experience are typed as freely as Salary. Every input below is a
 * real value taken from salary_survey-3.json.
 */
class RawJobRowTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "18,     18.0",
            "1.5,     1.5",   // decimals must not be truncated
            "0.5,     0.5",
            "5 years, 5.0",
            "1 of employment, 1.0",
            "10+ years, 10.0",
    })
    void parsesRealSurveyValues(String raw, double expected) {
        assertThat(record(raw).yearsAtEmployer()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" has no usable duration")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-1", "-3", "-16"}) // sentinel for "no answer", not a real value
    void negativeAndUnusableValuesBecomeNullNotAWrongSignFlip(String raw) {
        assertThat(record(raw).yearsAtEmployer())
                .as("a stripped sign must not turn a sentinel into a plausible-looking positive number")
                .isNull();
    }

    @ParameterizedTest(name = "\"{0}\" is preserved as the raw value even when unparsable")
    @ValueSource(strings = {"18", "-16", "1 of employment"})
    void rawTextSurvivesEvenWhenTheParsedValueIsNull(String raw) {
        assertThat(record(raw).yearsAtEmployerRaw())
                .as("normalising must never discard what the respondent actually typed")
                .isEqualTo(raw);
    }

    private static com.ata.jobdata.model.JobRecord record(String yearsAtEmployer) {
        RawJobRow row = new RawJobRow(
                "3/21/16 12:54", "Employer", "City, ST", "Job Title",
                yearsAtEmployer, null, "100000", null, null, null, "Male", null);
        return row.toJobRecord(1);
    }
}
