package com.ata.jobdata.repository;

import com.ata.jobdata.model.JobRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timestamp, Years at Employer and Years of Experience are typed as freely as Salary. Every input
 * below is a real value taken from salary_survey-3.json.
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
        assertThat(years(raw).yearsAtEmployer()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" has no usable duration")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-1", "-3", "-16"}) // sentinel for "no answer", not a real value
    void negativeAndUnusableValuesBecomeNullNotAWrongSignFlip(String raw) {
        assertThat(years(raw).yearsAtEmployer())
                .as("a stripped sign must not turn a sentinel into a plausible-looking positive number")
                .isNull();
    }

    @ParameterizedTest(name = "\"{0}\" is preserved as the raw value even when unparsable")
    @ValueSource(strings = {"18", "-16", "1 of employment"})
    void rawTextSurvivesEvenWhenTheParsedValueIsNull(String raw) {
        assertThat(years(raw).yearsAtEmployerRaw())
                .as("normalising must never discard what the respondent actually typed")
                .isEqualTo(raw);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "3/21/2016 13:11:18, 2016-03-21T13:11:18",
            "9/7/2016 5:14:48,   2016-09-07T05:14:48",  // unpadded hour, padded on the way out
            "1/10/2017 17:00:42, 2017-01-10T17:00:42",
            "12/31/2020 23:59:59, 2020-12-31T23:59:59",
    })
    void restatesTheSurveysUsDateAsFixedWidthIso(String raw, String expected) {
        assertThat(timestamp(raw).timestamp()).isEqualTo(expected);
    }

    @Test
    void isoTimestampsCompareChronologicallyAsPlainStrings() {
        String earlierButAlphabeticallyLater = timestamp("3/21/2016 13:11:18").timestamp();
        String laterButAlphabeticallyEarlier = timestamp("1/10/2017 17:00:42").timestamp();

        assertThat(earlierButAlphabeticallyLater)
                .as("the raw M/D/YYYY strings sort the wrong way round; ISO must not")
                .isLessThan(laterButAlphabeticallyEarlier);
    }

    @Test
    void keepsTheOriginalTimestampTextAlongsideTheIsoOne() {
        JobRecord record = timestamp("3/21/2016 13:11:18");

        assertThat(record.timestampRaw()).isEqualTo("3/21/2016 13:11:18");
    }

    @ParameterizedTest(name = "\"{0}\" is not a usable timestamp")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not a date", "3/21/16 12:54"}) // last one is the CSV's 2-digit-year form
    void unparsableTimestampsBecomeNull(String raw) {
        assertThat(timestamp(raw).timestamp()).isNull();
    }

    private static JobRecord years(String yearsAtEmployer) {
        return row("3/21/2016 13:11:18", yearsAtEmployer).toJobRecord(1);
    }

    private static JobRecord timestamp(String timestamp) {
        return row(timestamp, "3").toJobRecord(1);
    }

    private static RawJobRow row(String timestamp, String yearsAtEmployer) {
        return new RawJobRow(
                timestamp, "Employer", "City, ST", "Job Title",
                yearsAtEmployer, null, "100000", null, null, null, "Male", null);
    }
}
