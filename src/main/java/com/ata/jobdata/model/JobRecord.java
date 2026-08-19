package com.ata.jobdata.model;

/**
 * One survey row after normalisation. Every field is nullable — the source is a public survey where
 * most columns were optional.
 *
 * <p>{@code salary}/{@code yearsAtEmployer}/{@code yearsOfExperience}/{@code timestamp} are the
 * comparable normalised values; their {@code *Raw} counterparts are exactly what the source held, so
 * normalising never destroys the original answer — including for rows where normalising gave up and
 * returned null (e.g. a negative "no answer" sentinel).
 *
 * <p>{@code timestamp} is an ISO-8601 local date-time held as a fixed-width string. The survey
 * carries no time zone, so none is invented; the fixed width means lexicographic order is also
 * chronological order, which is what makes sorting and range filters on it correct for free.
 */
public record JobRecord(
        int id,
        String timestamp,
        String timestampRaw,
        String employer,
        String location,
        String jobTitle,
        Double yearsAtEmployer,
        String yearsAtEmployerRaw,
        Double yearsOfExperience,
        String yearsOfExperienceRaw,
        Long salary,
        String salaryCurrency,
        String salaryRaw,
        String signingBonus,
        String annualBonus,
        String annualStockValue,
        String gender,
        String additionalComments) {
}
