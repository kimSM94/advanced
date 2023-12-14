<h1>예제 만들</h1>

<h3>학습을 위한 간단한 예제 프로젝트를 만들어보자</h3>
일반적인 웹 애플리케이션에서 Controller Service Repository로 이어지는 흐름을 최대한 단순하게 만들어보자.

```
@Repository
@RequiredArgsConstructor
public class OrderRepositoryV0 {
   public void save(String itemId) {
   //저장 로직
     if (itemId.equals("ex")) {
         throw new IllegalStateException("예외 발생!");
     }
         sleep(1000);
     }
         private void sleep(int millis) {
         try {
         Thread.sleep(millis);
         } catch (InterruptedException e) {
         e.printStackTrace();
         }
       }
}
```

- @Repository : 컴포넌트 스캔의 대상이 된다. 따라서 스프링 빈으로 자동 등록된다.

```
@Service
@RequiredArgsConstructor
public class OrderServiceV0 {
 private final OrderRepositoryV0 orderRepository;

  public void orderItem(String itemId) {
   orderRepository.save(itemId);
   }

}
```

- @Service : 컴포넌트 스캔의 대상이 된다.
- 실무에서는 복잡한 비즈니스 로직이 서비스 계층에 포함되지만, 예제에서는 단순함을 위해서 리포지토리에 저장
을 호출하는 코드만 있다

```
@RestController
@RequiredArgsConstructor
public class OrderControllerV0 {
 private final OrderServiceV0 orderService;
 @GetMapping("/v0/request")
 public String request(String itemId) {
 orderService.orderItem(itemId);
 return "ok";
 }
}
```
- @RestController : 컴포넌트 스캔과 스프링 Rest 컨트롤러로 인식된다

<h3>로그 추적기 V1 - 프로토타입 개발</h3>

```
@Slf4j
@Component
public class HelloTraceV1 {
   private static final String START_PREFIX = "-->";
   private static final String COMPLETE_PREFIX = "<--";
   private static final String EX_PREFIX = "<X-";

   public TraceStatus begin(String message) {
     TraceId traceId = new TraceId();
     Long startTimeMs = System.currentTimeMillis();
     log.info("[{}] {}{}", traceId.getId(), addSpace(START_PREFIX, traceId.getLevel()), message);
     return new TraceStatus(traceId, startTimeMs, message);
   }
   public void end(TraceStatus status) {
     complete(status, null);
   }
   public void exception(TraceStatus status, Exception e) {
     complete(status, e);
   }
   private void complete(TraceStatus status, Exception e) {
     Long stopTimeMs = System.currentTimeMillis();
     long resultTimeMs = stopTimeMs - status.getStartTimeMs();
     TraceId traceId = status.getTraceId();
     if (e == null) {
         log.info("[{}] {}{} time={}ms", traceId.getId(), addSpace(COMPLETE_PREFIX, traceId.getLevel()), status.getMessage(),  resultTimeMs);
     } else {
         log.info("[{}] {}{} time={}ms ex={}", traceId.getId(), 
        addSpace(EX_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs, e.toString());
     }
 }
 private static String addSpace(String prefix, int level) {
   StringBuilder sb = new StringBuilder();
   for (int i = 0; i < level; i++) {
     sb.append( (i == level - 1) ? "|" + prefix : "| ");
   }
   return sb.toString();
 }
}
```

- @Component : 싱글톤으로 사용하기 위해 스프링 빈으로 등록한다. 컴포넌트 스캔의 대상이 된다

1. TraceStatus begin(String message)
- 로그를 시작한다.
- 로그 메시지를 파라미터로 받아서 시작 로그를 출력한다.
- 응답 결과로 현재 로그의 상태인 TraceStatus 를 반환한다.
2. void end(TraceStatus status)
- 로그를 정상 종료한다.
- 파라미터로 시작 로그의 상태( TraceStatus )를 전달 받는다. 이 값을 활용해서 실행 시간을 계산하고, 종료시에도 시작할 때와 동일한 로그 메시지를 출력할 수 있다.
정상 흐름에서 호출한다.
3. void exception(TraceStatus status, Exception e)
- 로그를 예외 상황으로 종료한다.
- TraceStatus , Exception 정보를 함께 전달 받아서 실행시간, 예외 정보를 포함한 결과 로그를 출력한다.
- 예외가 발생했을 때 호출한다

```
import hello.advanced.trace.TraceStatus;
import hello.advanced.trace.hellotrace.HelloTraceV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
@Repository
@RequiredArgsConstructor
public class OrderRepositoryV1 {
 private final HelloTraceV1 trace;
     public void save(String itemId) {
       TraceStatus status = null;
       try {
           status = trace.begin("OrderRepository.save()");
         //저장 로직
         if (itemId.equals("ex")) {
           throw new IllegalStateException("예외 발생!");
         }
         sleep(1000);
         trace.end(status);
         } catch (Exception e) {
           trace.exception(status, e);
           throw e;
         }
     }

     private void sleep(int millis) {
     try {
         Thread.sleep(millis);
     } catch (InterruptedException e) {
         e.printStackTrace();
     }
   }
}
```

- throw e : 예외를 꼭 다시 던져주어야 한다. 그렇지 않으면 여기서 예외를 먹어버리고, 이후에 정상 흐름으로 동
작한다. 로그는 애플리케이션에 흐름에 영향을 주면 안된다. 로그 때문에 예외가 사라지면 안된다


<h4>V2 적용하기</h4>
-메서드 호출의 깊이를 표현하고, HTTP 요청도 구분해보자.
- 이렇게 하려면 처음 로그를 남기는 OrderController.request() 에서 로그를 남길 때 어떤 깊이와 어떤 트랜잭
션 ID를 사용했는지 다음 차례인 OrderService.orderItem() 에서 로그를 남기는 시점에 알아야한다.
-결국 현재 로그의 상태 정보인 트랜잭션ID 와 level 이 다음으로 전달되어야 한다.

![image](https://github.com/kimSM94/advanced/assets/82505269/8c8f8ba9-52d8-47f5-a9c5-82d765d1782b)

```
package hello.advanced.app.v2;
import hello.advanced.trace.TraceId;
import hello.advanced.trace.TraceStatus;
import hello.advanced.trace.hellotrace.HelloTraceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class OrderServiceV2 {
     private final OrderRepositoryV2 orderRepository;
     private final HelloTraceV2 trace;
         public void orderItem(TraceId traceId, String itemId) {
         TraceStatus status = null;
         try {
             status = trace.beginSync(traceId, "OrderService.orderItem()");
             orderRepository.save(status.getTraceId(), itemId);
             trace.end(status);
         } catch (Exception e) {
             trace.exception(status, e);
             throw e;
         }
     }
}
```

- save() 는 파라미터로 전달 받은 traceId 를 사용해서 trace.beginSync() 를 실행한다.
- beginSync() 는 내부에서 다음 traceId 를 생성하면서 트랜잭션ID는 유지하고 level 은 하나 증가시킨다.
- beginSync() 는 이렇게 갱신된 traceId 로 새로운 TraceStatus 를 반환한다.
- trace.end(status) 를 호출하면서 반환된 TraceStatus 를 전달한다.

- orderItem() 은 파라미터로 전달 받은 traceId 를 사용해서 trace.beginSync() 를 실행한다.
- beginSync() 는 내부에서 다음 traceId 를 생성하면서 트랜잭션ID는 유지하고 level 은 하나 증가시킨다.
- beginSync() 가 반환한 새로운 TraceStatus 를 orderRepository.save() 를 호출하면서 파라미터로 전달한다.
- TraceId 를 파라미터로 전달하기 위해 orderRepository.save() 의 파라미터에 TraceId 를 추가해야 한다

``` 
@Repository
@RequiredArgsConstructor
public class OrderRepositoryV2 {
     private final HelloTraceV2 trace;
         public void save(TraceId traceId, String itemId) {
             TraceStatus status = null;
             try {
                 status = trace.beginSync(traceId, "OrderRepository.save()");
             //저장 로직
             if (itemId.equals("ex")) {
                 throw new IllegalStateException("예외 발생!");
             }
             sleep(1000);
             trace.end(status);
             } catch (Exception e) {
               trace.exception(status, e);
               throw e;
             }
             }

             private void sleep(int millis) {
               try {
                 Thread.sleep(millis);
               } catch (InterruptedException e) {
                 e.printStackTrace();
               }
     }
}
```
