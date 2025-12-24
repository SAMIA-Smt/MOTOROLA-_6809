
; TEST 2: loop + arithmetic + compare + branch + breakpoint stop


COUNT   EQU   $0100

        ORG   $E000
START:  LDA   #$05
        STA   COUNT

LOOP:   LDA   COUNT
        ADDA  #$01
        STA   COUNT
        CMPA  #$0A
        BNE   LOOP

        JMP   DONE

        ORG   $E050
DONE:   BRA   DONE

        ORG   $FFFE
        FDB   START
