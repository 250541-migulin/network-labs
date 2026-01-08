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
        ctx.writeLine("CTRL:DONE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:END");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:CLOSE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:BYE");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:EXIT");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:STOP");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:FINISH");
        ctx.writeLine(Protocol.CRLF);
        ctx.writeLine("CTRL:OVER");
        ctx.writeLine(Protocol.CRLF);
    }
}
