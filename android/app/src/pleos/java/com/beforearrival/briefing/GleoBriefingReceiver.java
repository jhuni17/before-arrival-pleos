package com.beforearrival.briefing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public final class GleoBriefingReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        BriefingMediaService service = BriefingMediaService.activeSession();
        boolean success;
        String message;
        switch (action) {
            case "NEXT_BRIEFING_TOPIC":
                success = service != null && service.nextTopic();
                message = success ? "Moved to the next briefing topic." : "There is no next briefing topic.";
                break;
            case "PAUSE_COMMUTE_BRIEFING":
                success = service != null && service.pauseBriefing();
                message = success ? "Paused the commute briefing." : "There is no playing briefing to pause.";
                break;
            case "RESUME_COMMUTE_BRIEFING":
                success = service != null && service.resumeBriefing();
                message = success ? "Resumed the commute briefing." : "There is no active briefing to resume.";
                break;
            default:
                return;
        }
        String status = success ? "success" : "fail";
        String response = "{\"functionName\":" + JSONObject.quote(action) + ",\"status\":"
                + JSONObject.quote(status) + ",\"message\":" + JSONObject.quote(message) + "}";
        context.sendBroadcast(new Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .setPackage("ai.umos.ambientai")
                .putExtra("result", status)
                .putExtra("packageName", context.getPackageName())
                .putExtra("response", response));
        PleosVerificationTrace.record(context, "gleoAction=" + action + " status=" + status);
    }
}
