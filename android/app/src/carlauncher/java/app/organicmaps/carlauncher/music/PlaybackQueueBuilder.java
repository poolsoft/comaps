package app.organicmaps.carlauncher.music;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds an immutable snapshot of the visible source list and resolves the
 * selected track by stable identity before playback state is changed.
 */
public final class PlaybackQueueBuilder {

    private PlaybackQueueBuilder() {
    }

    @Nullable
    public static Selection select(@Nullable List<MusicRepository.AudioTrack> source,
            @Nullable MusicRepository.AudioTrack selectedTrack) {
        if (source == null || source.isEmpty() || selectedTrack == null) {
            return null;
        }
        List<MusicRepository.AudioTrack> queue = new ArrayList<>(source.size());
        int selectedIndex = -1;
        for (MusicRepository.AudioTrack track : source) {
            if (track == null) {
                continue;
            }
            if (selectedIndex < 0 && MusicTrackIdentity.matches(track, selectedTrack)) {
                selectedIndex = queue.size();
            }
            queue.add(track);
        }
        if (queue.isEmpty() || selectedIndex < 0) {
            return null;
        }
        return new Selection(queue, selectedIndex);
    }

    public static final class Selection {
        private final List<MusicRepository.AudioTrack> queue;
        private final int selectedIndex;

        private Selection(List<MusicRepository.AudioTrack> queue, int selectedIndex) {
            this.queue = Collections.unmodifiableList(queue);
            this.selectedIndex = selectedIndex;
        }

        @NonNull
        public List<MusicRepository.AudioTrack> getQueue() {
            return queue;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        @NonNull
        public MusicRepository.AudioTrack getSelectedTrack() {
            return queue.get(selectedIndex);
        }
    }
}
