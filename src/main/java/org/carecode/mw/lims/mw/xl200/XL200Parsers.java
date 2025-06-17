package org.carecode.mw.lims.mw.xl200;

import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class XL200Parsers {

    public static PatientRecord parsePatientRecord(String record) {
        String[] fields = record.split("\\|");
        String patientId = fields.length > 2 ? fields[2] : "";
        String name = fields.length > 4 ? fields[4] : "";
        String sex = fields.length > 7 ? fields[7] : "";
        String address = fields.length > 11 ? fields[11] : "";
        String phone = fields.length > 14 ? fields[14] : "";
        String doctor = fields.length > 15 ? fields[15] : "";

        return new PatientRecord(1, patientId, "", name, "", sex, "", "", address, phone, doctor);
    }

    public static QueryRecord parseQueryRecord(String record) {
        String[] fields = record.split("\\|");
        String[] parts = fields.length > 2 ? fields[2].split("\\^") : new String[0];
        String sampleId = parts.length > 1 ? parts[1] : parts[0];
        return new QueryRecord(0, sampleId, "", "");
    }

    public static ResultsRecord parseResultsRecord(String record) {
        String[] fields = record.split("\\|");
        String testCode = fields.length > 2 ? fields[2].split("\\^").length > 3 ? fields[2].split("\\^")[3] : fields[2] : "";
        String resultValue = fields.length > 3 ? fields[3] : "";
        String unit = fields.length > 4 ? fields[4] : "";
        String refRange = fields.length > 5 ? fields[5] : "";
        String resultDateTime = fields.length > 12 ? fields[12] : "";

        return new ResultsRecord(1, testCode, resultValue, unit, resultDateTime, "XL_200", "");
    }
}
