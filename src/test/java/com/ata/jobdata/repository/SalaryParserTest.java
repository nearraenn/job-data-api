package com.ata.jobdata.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every input below is a real value taken from salary_survey-3.json. This is the test that matters:
 * if it breaks, ?salary[gte]=120000 silently returns the wrong rows rather than failing loudly.
 */
class SalaryParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource(nullValues = "null", value = {
            // plain and grouped
            "'122000',      122000,   null",
            "'83,000',       83000,   null",
            "'15,00,000 Rs', 1500000, INR",   // Indian grouping
            "'23 000$',      23000,   USD",   // space as separator
            "'110,406.48',   110406,  null",  // decimals are rounded away
            // currency symbols and codes
            "'$120,000',     120000,  USD",
            "'€60,000',      60000,   EUR",
            "'£60000',       60000,   GBP",
            "'$84,500 AUD',  84500,   AUD",   // an explicit code beats the $ symbol
            "'102,000 CDN',  102000,  CAD",   // normalised to ISO
            "'$960000HKD',   960000,  HKD",   // code glued to the number
            "'19800 euro',   19800,   EUR",
            "'250,000 yen',  250000,  JPY",
            "'180000 USD / yr', 180000, USD",
            // magnitude suffixes
            "'70k',          70000,   null",
            "'100K',         100000,  null",
            "'61.7k €',      61700,   EUR",
            "'36.5K USD / YEAR', 36500, USD",
            "'5.5 Million JPY',  5500000, JPY",
            "'7.2M JPY',     7200000, JPY",
            // rates annualised
            "'$30/hr',       62400,   USD",   // 30 x 2080
            "'$35 per hour',  72800,  USD",
            "'1500 / day corp2corp no bennies', 390000, null", // 1500 x 260
            // ranges take the lower bound
            "'110000-120000', 110000, null",
            "'10468 - really', 10468, null",
    })
    void parsesRealSurveyValues(String raw, Long expectedAmount, String expectedCurrency) {
        SalaryParser.Money money = SalaryParser.parse(raw);

        assertThat(money.amount()).isEqualTo(expectedAmount);
        assertThat(money.currency()).isEqualTo(expectedCurrency);
    }

    @ParameterizedTest(name = "\"{0}\" has no usable salary")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "-",              // explicit "no answer"
            "-1",             // below the plausibility floor
            "1 rare pepe",    // a joke answer
            "1.3",            // ambiguous, almost certainly not an annual figure
            "108 AUD",        // an hourly or daily rate typed without a unit
    })
    void reportsUnusableValuesAsUnknownRatherThanZero(String raw) {
        assertThat(SalaryParser.parse(raw).amount())
                .as("unknown must stay null so it is excluded from numeric filters, not treated as 0")
                .isNull();
    }

    @Test
    void keepsTheCurrencyEvenWhenTheAmountIsUnusable() {
        assertThat(SalaryParser.parse("108 AUD").currency()).isEqualTo("AUD");
    }
}
