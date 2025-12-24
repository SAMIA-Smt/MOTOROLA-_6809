
; TEST 3: indexed ,X+ + SWI vector + handler + breakpoint
; (no LABEL+N syntax)


BUF0    EQU   $0100
BUF1    EQU   $0101
BUF2    EQU   $0102
BUF3    EQU   $0103

        ORG   $E000
START:  LDX   #BUF0
        LDA   #$12
        STA   ,X+
        LDA   #$34
        STA   ,X+
        SWI
        JMP   DONE

AFTER:  LDA   #$99
        STA   BUF2
        JMP   DONE

        ORG   $E050
DONE:   BRA   DONE

        ORG   $E100
SWI_HANDLER:
        LDA   #$77
        STA   BUF3
        JMP   AFTER

        ORG   $FFFA       ; SWI vector
        FDB   SWI_HANDLER

        ORG   $FFFE       ; RESET vector
        FDB   START
