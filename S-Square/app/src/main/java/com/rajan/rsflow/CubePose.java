package com.rajan.rsflow;

/** The three oriented faces needed to describe any of the cube's 24 poses. */
record CubePose(int front, int right, int top) {
    private static final int[] OPPOSITE = {2, 3, 0, 1, 5, 4};
    private static final int[][] NORMALS = {
        {0, 0, 1}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 0}, {0, -1, 0}, {0, 1, 0}
    };
    static final CubePose INITIAL = new CubePose(0, 1, 4);

    static int opposite(int face) {
        return OPPOSITE[face];
    }

    CubePose turn(int direction, boolean vertical) {
        if (vertical) {
            return direction > 0
                    ? new CubePose(top, right, OPPOSITE[front])
                    : new CubePose(OPPOSITE[top], right, front);
        }
        return direction > 0
                ? new CubePose(OPPOSITE[right], front, top)
                : new CubePose(right, OPPOSITE[front], top);
    }

    int encode() {
        return front + 6 * right + 36 * top;
    }

    static CubePose decode(int encoded) {
        if (encoded < 0 || encoded >= 216) return INITIAL;
        CubePose pose = new CubePose(encoded % 6, (encoded / 6) % 6, encoded / 36);
        int[] front = NORMALS[pose.front], top = NORMALS[pose.top], right = NORMALS[pose.right];
        if (front[1] * top[2] - front[2] * top[1] != right[0]
                || front[2] * top[0] - front[0] * top[2] != right[1]
                || front[0] * top[1] - front[1] * top[0] != right[2]) return INITIAL;
        return pose;
    }
}
