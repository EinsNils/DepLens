package dev.nz3x.deplens.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestSequencerTest {

    @Test
    void onlyTheNewestRequestIsCurrent() {
        RequestSequencer sequencer = new RequestSequencer();

        long first = sequencer.next();
        assertThat(sequencer.isCurrent(first)).isTrue();

        long second = sequencer.next();
        assertThat(sequencer.isCurrent(first)).isFalse();
        assertThat(sequencer.isCurrent(second)).isTrue();
    }
}
