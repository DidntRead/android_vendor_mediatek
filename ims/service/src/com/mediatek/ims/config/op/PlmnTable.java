package com.mediatek.ims.config.op;

import java.util.ArrayList;

import com.mediatek.ims.config.ImsConfigContract.Operator;

/**
 * A table maps mcc/mnc to operator code.
 */

public class PlmnTable {

    public static class Entry {
        public Entry(int _start, int _end, String _operatorCode) {
            mccMncRangeStart = _start;
            mccMncRangeEnd = _end;
            operatorId = _operatorCode;
        }
        int mccMncRangeStart;
        int mccMncRangeEnd;
        String operatorId;

        public boolean exist(int plmn) {
            return (plmn >= mccMncRangeStart && plmn <= mccMncRangeEnd) ? true : false;
        }
    };

    static ArrayList<Entry> sTable = new ArrayList<Entry>();

    static {
        // OP06
        sTable.add(new Entry(23415, 23415, Operator.OP_06)); // United Kingdom
        sTable.add(new Entry(23427, 23427, Operator.OP_06)); // United Kingdom
        sTable.add(new Entry(23491, 23491, Operator.OP_06)); // United Kingdom
        sTable.add(new Entry(22206, 22206, Operator.OP_06)); // Italy
        sTable.add(new Entry(22210, 22210, Operator.OP_06)); // Italy
        sTable.add(new Entry(26202, 26202, Operator.OP_06)); // Germany
        sTable.add(new Entry(26204, 26204, Operator.OP_06)); // Germany
        sTable.add(new Entry(26209, 26209, Operator.OP_06)); // Germany
        sTable.add(new Entry(21401, 21401, Operator.OP_06)); // Spain
        sTable.add(new Entry(21406, 21406, Operator.OP_06)); // Spain

        // OP08
        sTable.add(new Entry(31031, 31031, Operator.OP_08)); //United States
        sTable.add(new Entry(310160, 310160, Operator.OP_08)); //United States
        sTable.add(new Entry(310200, 310200, Operator.OP_08)); //United States
        sTable.add(new Entry(310210, 310210, Operator.OP_08)); //United States
        sTable.add(new Entry(310220, 310220, Operator.OP_08)); //United States
        sTable.add(new Entry(310230, 310230, Operator.OP_08)); //United States
        sTable.add(new Entry(310240, 310240, Operator.OP_08)); //United States
        sTable.add(new Entry(310250, 310250, Operator.OP_08)); //United States
        sTable.add(new Entry(310260, 310260, Operator.OP_08)); //United States
        sTable.add(new Entry(310270, 310270, Operator.OP_08)); //United States
        sTable.add(new Entry(310280, 310280, Operator.OP_08)); //United States
        sTable.add(new Entry(310300, 310300, Operator.OP_08)); //United States
        sTable.add(new Entry(310310, 310310, Operator.OP_08)); //United States
        sTable.add(new Entry(310330, 310330, Operator.OP_08)); //United States
        sTable.add(new Entry(310660, 310660, Operator.OP_08)); //United States
        sTable.add(new Entry(310800, 310800, Operator.OP_08)); //United States

        // OP12
        sTable.add(new Entry(31004, 31004, Operator.OP_12)); //United States
        sTable.add(new Entry(31010, 31010, Operator.OP_12)); //United States
        sTable.add(new Entry(31012, 31013, Operator.OP_12)); //United States
        sTable.add(new Entry(310590, 310590, Operator.OP_12)); //United States
        sTable.add(new Entry(310890, 310890, Operator.OP_12)); //United States
        sTable.add(new Entry(310910, 310910, Operator.OP_12)); //United States
        sTable.add(new Entry(311110, 311110, Operator.OP_12)); //United States
        sTable.add(new Entry(311270, 311289, Operator.OP_12)); //United States
        sTable.add(new Entry(311390, 311390, Operator.OP_12)); //United States
        sTable.add(new Entry(311480, 311489, Operator.OP_12)); //United States
    }

    public static String getOperatorCode(int plmn) {
        for (Entry e : sTable) {
            if (e.exist(plmn)) {
                return e.operatorId;
            }
        }
        return Operator.OP_DEFAULT;
    }
}
