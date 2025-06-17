package org.carecode.mw.lims.mw.xl200;

import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;
import org.carecode.lims.libraries.OrderRecord;

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
        String sampleId = "";
        if (fields.length > 2) {
            String[] parts = fields[2].split("\\^");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sampleId = part;
                    break;
                }
            }
        }

        // ASTM query records may optionally specify one or more test codes in
        // the universal test ID field (typically field index 4). These are
        // separated by backslashes when multiple tests are requested. Extract
        // them so they can be forwarded to the LIMS if present.
        String testCodes = "";
        if (fields.length > 4 && !fields[4].isEmpty()) {
            String[] tests = fields[4].split("\\\\");
            StringBuilder parsed = new StringBuilder();
            for (int i = 0; i < tests.length; i++) {
                String[] parts = tests[i].split("\\^");
                String code = parts.length > 3 ? parts[3]
                        : parts[parts.length - 1];
                if (!code.isEmpty()) {
                    if (parsed.length() > 0) {
                        parsed.append(',');
                    }
                    parsed.append(code);
                }
            }
            testCodes = parsed.toString();
        }

        return new QueryRecord(0, sampleId, sampleId, testCodes);
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

    /**
     * Parse an ASTM order record.
     *
     * <p>The XL-200 instrument follows the common ASTM structure where the
     * sample id is provided in field 2 and the universal test identifier in
     * field 4.  The universal test identifier may contain multiple components
     * separated by carets.  The actual test code is usually found at position
     * 4 within that field, falling back to the last non empty component when
     * fewer parts are present.</p>
     */
    public static OrderRecord parseOrderRecord(String record) {
        String[] fields = record.split("\\|");

        // Sample identifier (field 2) may contain empty components separated by
        // carets.  Use the first non empty part just like query records do.
        String sampleId = "";
        if (fields.length > 2) {
            String[] parts = fields[2].split("\\^");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sampleId = part;
                    break;
                }
            }
        }

        // Extract the requested test code from the universal test ID (field 4).
        String testCode = "";
        if (fields.length > 4 && !fields[4].isEmpty()) {
            String[] parts = fields[4].split("\\^");
            if (parts.length > 3 && !parts[3].isEmpty()) {
                testCode = parts[3];
            } else if (parts.length > 0) {
                testCode = parts[parts.length - 1];
            }
        }

        // Priority (field 5) if present.
        String priority = fields.length > 5 ? fields[5] : "";

        return new OrderRecord(1, sampleId, sampleId, testCode, priority);
    }
}
