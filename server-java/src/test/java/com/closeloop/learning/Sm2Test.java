package com.closeloop.learning;

import com.closeloop.state.model.Kp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SM-2 调度表驱动单测：(q, reviews, interval) → due 间隔/状态 */
class Sm2Test {

    private static Kp.KpState st(double ease, int interval, int reviews, double stability, String last) {
        Kp.KpState s = new Kp.KpState();
        s.ease = ease;
        s.interval = interval;
        s.reviews = reviews;
        s.stability = stability;
        s.lastReviewDate = last;
        s.due = last;
        return s;
    }

    @Test
    void levelToQMapping() {
        assertEquals(5, Sm2.levelToQ("优秀"));
        assertEquals(4, Sm2.levelToQ("良好"));
        assertEquals(3, Sm2.levelToQ("及格"));
        assertEquals(2, Sm2.levelToQ("不及格"));
        assertEquals(1, Sm2.levelToQ("空白"));
        assertEquals(1, Sm2.levelToQ(null));
    }

    @Test
    void failResetsIntervalTo1() {
        Kp.KpState s = st(2.5, 6, 3, 5.0, "2026-09-01");
        Sm2.update(s, 1);
        assertEquals(1, s.interval);
        assertEquals("relearning", s.status);
        assertEquals(4, s.reviews);
    }

    @Test
    void firstGoodReviewInterval1() {
        Kp.KpState s = st(2.5, 0, 0, 0, null);
        Sm2.update(s, 4);
        assertEquals(1, s.interval);
        assertEquals(1, s.reviews);
        assertEquals("learning", s.status);
    }

    @Test
    void secondGoodReviewInterval6() {
        Kp.KpState s = st(2.5, 1, 1, 2.5, "2026-09-01");
        Sm2.update(s, 5);
        assertEquals(6, s.interval);
    }

    @Test
    void easeClampedIn13to28() {
        Kp.KpState s = st(2.8, 6, 3, 5.0, "2026-09-01");
        Sm2.update(s, 5);
        assertTrue(s.ease <= 2.8);
        Kp.KpState s2 = st(1.3, 6, 3, 5.0, "2026-09-01");
        Sm2.update(s2, 1);
        assertTrue(s2.ease >= 1.3);
    }

    @Test
    void retrievabilityDecays() {
        Kp.KpState s = st(2.5, 10, 5, 5.0, "2026-09-01");
        double r0 = Sm2.retrievability(s, "2026-09-01");
        double r5 = Sm2.retrievability(s, "2026-09-06");
        assertEquals(1.0, r0, 1e-6);
        assertTrue(r5 < r0);
        assertTrue(r5 > 0);
    }
}
