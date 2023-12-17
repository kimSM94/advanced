package hello.advanced.trace.threadlocal.code;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadLocalService {

    private ThreadLocal<String> nameStore = new ThreadLocal<>();

    public String logic(String name) {
        log.info("저장 name={} -> nameStore= {}", name, nameStore.get());
        nameStore.set(name);
        sleep(2000); // 동시성문제 발생X
        sleep(10); // 동시성문제 발생 O
        log.info("조회 nameStore={}", nameStore.get());
        return nameStore.get();
    }


    private void sleep(int miils) {
        try {
            Thread.sleep(miils);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
