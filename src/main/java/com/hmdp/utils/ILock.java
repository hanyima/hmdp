package com.hmdp.utils;

public interface ILock {
    /**
     *
     * @param timeoutSec
     * @return true获取成功，false获取失败
     */
    public boolean tryLock(Long timeoutSec);

    /**
     *释放锁
     */
    public void unlock();
}
