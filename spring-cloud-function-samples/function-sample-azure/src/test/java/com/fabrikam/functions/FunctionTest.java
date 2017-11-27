//package com.fabrikam.functions;
//
//import org.junit.Test;
//import com.microsoft.azure.serverless.functions.*;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.logging.Logger;
//
//import static org.junit.Assert.assertSame;
//import static org.mockito.ArgumentMatchers.anyInt;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.doReturn;
//import static org.mockito.Mockito.mock;
//
//
///**
// * Unit test for Function class.
// */
//public class FunctionTest {
//    /**
//     * Unit test for hello method.
//     */
//    @Test
//    public void testHello() throws Exception {
//        // Setup
//        final HttpRequestMessage req = mock(HttpRequestMessage.class);
//
//        final Map<String, String> queryParams = new HashMap<>();
//        queryParams.put("name", "Azure");
//        doReturn(queryParams).when(req).getQueryParameters();
//
//        final HttpResponseMessage res = mock(HttpResponseMessage.class);
//        doReturn(res).when(req).createResponse(anyInt(), anyString());
//
//        final ExecutionContext context = mock(ExecutionContext.class);
//        doReturn(Logger.getGlobal()).when(context).getLogger();
//
//        // Invoke
//        final HttpResponseMessage ret = new Function().hello(req, context);
//
//        // Verify
//        assertSame(res, ret);
//    }
//}
