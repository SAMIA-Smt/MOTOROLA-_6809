
; TEST 1: directives + ROM gaps + RMB + RESET vector


        ORG   $E000
START:  LDA   #$42
        STA   $0100
        LDA   #$99
        STA   $0101
        JMP   DONE

; ---- ORG gap test (bytes between end of code and $E010 must stay unchanged) ----
        ORG   $E010
DATA1:  FCB   $AA,$BB,$CC

; ---- RMB reserve test (these 4 bytes must stay unchanged) ----
        ORG   $E020
RES:    RMB   4
        FCB   $DD

        ORG   $E030
DONE:   BRA   DONE

; ---- explicit RESET vector (big-endian) ----
        ORG   $FFFE
        FDB   START
