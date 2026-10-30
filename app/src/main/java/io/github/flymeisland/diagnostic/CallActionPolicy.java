package io.github.flymeisland.diagnostic;

import java.util.Locale;

/** Classify only explicit call-control labels; unknown actions must stay disabled. */
final class CallActionPolicy {
    enum Kind { ANSWER, REJECT, HANGUP, SPEAKER_ON, SPEAKER_OFF, SILENCE, UNKNOWN }
    static Kind classify(String label) {
        if (label == null) return Kind.UNKNOWN;
        String s = label.trim().toLowerCase(Locale.ROOT);
        if (s.equals("接听") || s.equals("接聽") || s.equals("answer") || s.equals("accept")) return Kind.ANSWER;
        if (s.equals("挂断") || s.equals("掛斷") || s.equals("hang up")) return Kind.HANGUP;
        if (s.equals("打开扬声器") || s.equals("開啟揚聲器") || s.equals("speaker on")) return Kind.SPEAKER_ON;
        if (s.equals("关闭扬声器") || s.equals("關閉揚聲器") || s.equals("speaker off")) return Kind.SPEAKER_OFF;
        if (s.equals("拒接") || s.equals("拒绝") || s.equals("拒絕") || s.equals("decline") || s.equals("reject")) return Kind.REJECT;
        if (s.equals("静音") || s.equals("靜音") || s.equals("silence")) return Kind.SILENCE;
        return Kind.UNKNOWN;
    }
}
