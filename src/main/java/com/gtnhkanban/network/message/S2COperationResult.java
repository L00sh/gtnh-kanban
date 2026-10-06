package com.gtnhkanban.network.message;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class S2COperationResult implements IMessage {

    private boolean success;
    private String code = "";
    private String message = "";

    public S2COperationResult() {}

    public S2COperationResult(boolean success, String code, String message) {
        this.success = success;
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(success);
        PacketData.writeString(buffer, code);
        PacketData.writeString(buffer, message);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        success = buffer.readBoolean();
        code = PacketData.readString(buffer, 128);
        message = PacketData.readString(buffer, 1024);
    }
}
