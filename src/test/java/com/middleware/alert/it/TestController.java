package com.middleware.alert.it;

import com.middleware.alert.exception.AlertableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟业务接口。
 */
@RestController
public class TestController {

    @GetMapping("/it/biz")
    public String biz() {
        throw new AlertableException("order failed", "E100");
    }

    @GetMapping("/it/npe")
    public String npe() {
        throw new NullPointerException("npe");
    }

    @GetMapping("/it/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(200L);
        return "ok";
    }
}
