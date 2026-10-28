package com.stayhub.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.payment.dto.PaymentStatusResponse;
import com.stayhub.payment.dto.VnpayIpnResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

class DemoReturnSmokeTest {
    @Test void enabledPostChecksOwnerBeforePassingOriginalMapToLifecycle() throws Exception {
        var payments=mock(PaymentService.class);
        var lifecycle=mock(InventoryPaymentLifecycleService.class);
        var ownership=mock(InventoryPaymentStatusService.class);
        var controller=new PaymentController(payments,lifecycle,ownership);
        ReflectionTestUtils.setField(controller,"demoReturnVerificationEnabled",true);
        var principal=mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(7L);
        when(ownership.findByAttemptReference(7L,"REF")).thenReturn(PaymentStatusResponse.builder().build());
        var params=Map.of("vnp_TxnRef","REF","vnp_SecureHash","original");
        when(lifecycle.handleInventoryIpn(params)).thenReturn(new VnpayIpnResponse("97","Invalid provider verification"));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter p) { return p.getParameterType()==UserPrincipal.class; }
            public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b) { return principal; }
        }).build();
        mvc.perform(post("/api/v1/payments/vnpay/verify").contentType("application/json")
                .content("{\"params\":{\"vnp_TxnRef\":\"REF\",\"vnp_SecureHash\":\"original\"}}"))
                .andExpect(status().isBadRequest());
        var order=inOrder(ownership,lifecycle);
        order.verify(ownership).findByAttemptReference(7L,"REF");
        order.verify(lifecycle).handleInventoryIpn(params);
        verifyNoInteractions(payments);
        when(lifecycle.handleInventoryIpn(params)).thenReturn(new VnpayIpnResponse("00","Confirm success"));
        mvc.perform(post("/api/v1/payments/vnpay/verify").contentType("application/json")
                .content("{\"params\":{\"vnp_TxnRef\":\"REF\",\"vnp_SecureHash\":\"original\"}}"))
                .andExpect(status().isOk());
    }

    @Test void disabledPostNeverTouchesPaymentServices() throws Exception {
        var payments=mock(PaymentService.class);
        var lifecycle=mock(InventoryPaymentLifecycleService.class);
        var ownership=mock(InventoryPaymentStatusService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(payments,lifecycle,ownership)).build();
        mvc.perform(post("/api/v1/payments/vnpay/verify").contentType("application/json")
                .content("{\"params\":{\"vnp_TxnRef\":\"REF\"}}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(payments,lifecycle,ownership);
    }
}
