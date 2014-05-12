package com.trik.gamepad;

final class Scripts {
    public static final String FORWARD = "brick.motor(\"JM3\").setPower(brick.motor(\"JM3\").power() + 20);"
                                               + "brick.motor(\"M1\").setPower(brick.motor(\"M1\").power() + 20);"
                                               + "brick.run();";

    public static final String BACK    = "brick.motor(\"JM3\").setPower(brick.motor(\"JM3\").power() - 20);"
                                               + "brick.motor(\"M1\").setPower(brick.motor(\"M1\").power() - 20);"
                                               + "brick.run();";

    public static final String STEER   = "brick.motor(\"JE1\").setPower(@@STEERING@@);"
                                               + "brick.run();";
}
