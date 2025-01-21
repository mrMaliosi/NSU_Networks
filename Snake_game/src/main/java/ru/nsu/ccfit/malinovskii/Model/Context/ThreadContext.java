package ru.nsu.ccfit.malinovskii.Model.Context;

import ru.nsu.ccfit.malinovskii.Thread.Listener;
import ru.nsu.ccfit.malinovskii.Thread.MasterSender;

public class ThreadContext {
    private static volatile ThreadContext threadContext;
    private Listener listener;

    private ThreadContext() {

    }

    // Метод для получения единственного экземпляра
    public static ThreadContext getContext() {
        if (threadContext == null) {
            synchronized (ThreadContext.class) {          // Синхронизация для обеспечения потокобезопасности
                if (threadContext == null) {
                    threadContext = new ThreadContext();     // Создание экземпляра, если его нет
                }
            }
        }
        return threadContext;
    }

    public Listener getListener() {
        return listener;
    }

    public void addListener(Listener listener){
        this.listener = listener;
    }

}
