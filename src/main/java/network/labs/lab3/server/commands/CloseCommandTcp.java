package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class CloseCommandTcp implements Command<TcpNioContext> {
    @Override
    public String name() { return "CLOSE"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        ctx.writeLine("TCP connection closed");
        ctx.remote().getAddress(); // просто демонстрация, можно логировать
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("Bye!");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("DONE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("END");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CLOSE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("BYE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("EXIT");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("STOP");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("FINISH");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("OVER");
        ctx.writeLine(Protocol.CRLF);
    }
}
