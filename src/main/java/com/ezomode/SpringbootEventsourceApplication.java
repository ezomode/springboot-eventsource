package com.ezomode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rx.Observable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

@RestController
@SpringBootApplication
public class SpringbootEventsourceApplication {

    static final int PERIOD_MILLIS = 1000;

    static int i = 0;

    static Thread t = new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(PERIOD_MILLIS);
            } catch (InterruptedException ignored) {
            }

            i++;
        }
    });

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SpringbootEventsourceApplication.class, args);

        ConfigurableEnvironment env = context.getEnvironment();

        Object[] properties = StreamSupport.stream(env.getPropertySources().spliterator(), true)
                .filter(t1 -> t1 instanceof PropertiesPropertySource)
                .map(propertySource -> (PropertiesPropertySource) propertySource)
                .map(MapPropertySource::getPropertyNames)
                .flatMap(Arrays::stream)
                .map((s) -> s + "=" + env.getProperty(s))
                .toArray();

        System.out.println("Active profiles: " + Arrays.toString(env.getActiveProfiles()));
        System.out.println("Active properties: " + Arrays.toString(properties));

        t.start();
    }

    @RequestMapping(value = "stream", method = RequestMethod.GET)
    public SseEmitter getStream() {
        SseEmitter emitter = new SseEmitter();

        runWithTimer(PERIOD_MILLIS, emitter);

        return emitter;
    }

    @RequestMapping(value = "rxstream", method = RequestMethod.GET)
    public SseEmitter getRxStream() {
        SseEmitter emitter = new SseEmitter();

        Observable<Long> responseObservable = Observable.interval(PERIOD_MILLIS, TimeUnit.MILLISECONDS);

        responseObservable.subscribe(
                counter -> {
                    try {
                        emitter.send("EMIT #" + counter);
                        System.out.println("Sending #" + counter);
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                },
                emitter::completeWithError, emitter::complete);

        return emitter;
    }

    private void runWithTimer(int period, final SseEmitter emitter) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendToEmitter(timer, emitter);
            }
        }, 0, period);
    }

    private void sendToEmitter(Timer timer, SseEmitter emitter) {
        try {
            emitter.send("EMIT #" + i);
            System.out.println("Sending #" + i);
        } catch (Throwable ignored) {
        }
    }
}
