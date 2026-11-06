package pro.eng.yui.android.osmjppostalmap.core;

public final class IndoorMapNote {

    private IndoorMapNote() {
    }

    public static String appendIndoorLocation(String memo, boolean indoor, String indoorMessage) {
        if (!indoor || indoorMessage == null || indoorMessage.isEmpty()) {
            return memo;
        }
        if (memo == null || memo.isEmpty()) {
            return indoorMessage;
        }
        return memo + "\n" + indoorMessage;
    }
}