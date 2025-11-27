package com.ssafy.keeping.domain.otp.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpSmsSender implements SmsSender {

    private final DefaultMessageService messageService;
    private final SmsProperties props;

    @Override
    public SingleMessageSentResponse send(String to, String text) {
        Message message = new Message();

        message.setFrom(props.getSendNumber());
        message.setTo(to);
        message.setText(text);

        SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message));
        log.warn("response : {}", response);

        return response;
    }
}
