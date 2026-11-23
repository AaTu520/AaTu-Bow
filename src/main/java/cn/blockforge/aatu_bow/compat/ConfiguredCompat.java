package cn.blockforge.aatu_bow.compat;

import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;

/** Copies the loaded values into Configured's update batch before it applies edited values. */
public final class ConfiguredCompat {
    private ConfiguredCompat() {
    }

    public static void copyValues(Config target, UnmodifiableConfig source) {
        for (UnmodifiableConfig.Entry entry : source.entrySet()) {
            // A list path preserves literal dots in a key instead of treating them as separators.
            List<String> path = List.of(entry.getKey());
            target.set(path, copyValue(target, entry.getValue()));
            if (target instanceof CommentedConfig comments
                    && source instanceof UnmodifiableCommentedConfig original) {
                comments.setComment(path, original.getComment(path));
            }
        }
    }

    private static Object copyValue(Config target, Object value) {
        if (value instanceof UnmodifiableConfig category) {
            Config copy = target.createSubConfig();
            copyValues(copy, category);
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object entry : list) {
                copy.add(copyValue(target, entry));
            }
            return copy;
        }
        return value;
    }
}
