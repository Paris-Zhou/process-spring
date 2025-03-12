## **Spring 创建上下文和bean工厂和注册bean定义信息的后续流程**

------

### **1. `getBean()`**

**作用**：获取 Bean 实例，可能会触发创建。
 **核心逻辑**：

- 先检查缓存（`singletonObjects`），如果已经存在，直接返回。
- 如果没有，调用 `doGetBean()` 进行创建。

------

### **2. `doGetBean()`**

**作用**：检查 Bean 是否已创建，若未创建则调用 `createBean()`。

**关键代码**：

```java
Object sharedInstance = getSingleton(beanName, () -> {
    return createBean(beanName, mbd, args);
});
```

- 这里 `getSingleton()` 主要是 **解决循环依赖**。
- `createBean()` 负责真正的 Bean 创建。

------

### **3. `createBean()`**

**作用**：创建 Bean 实例，调用 `doCreateBean()`。
 **核心逻辑**：

- 判断是否是 FactoryBean（Spring 的特殊 Bean）。
- 执行 `doCreateBean()`。

------

### **4. `doCreateBean()`**

**作用**：完整创建 Bean 实例，核心方法。
 **执行步骤**：

1. **创建实例**（反射 `newInstance()`）
2. **执行 `populateBean()`**（依赖注入）
3. **执行 `initializeBean()`**（初始化）

------

### **5. `populateBean()`**

**作用**：完成属性注入，依赖管理。
 **主要逻辑**：

- 解析 `@Autowired`、`@Value`，通过 `BeanFactory` 注入依赖。

------

### **6. `initializeBean()`**

**作用**：执行初始化逻辑，包括 `Aware` 接口、`@PostConstruct`、`BeanPostProcessor` 和 AOP 代理创建。

#### **6.1 执行 `invokeAwareMethods()`**

- 如果 Bean 实现了 `BeanNameAware`, `BeanFactoryAware`，会调用 `setBeanFactory()`。

#### **6.2 执行 `applyBeanPostProcessorsBeforeInitialization()`**

- 这里执行 `@PostConstruct`（`CommonAnnotationBeanPostProcessor`）。

#### **6.3 执行 `invokeInitMethods()`**

- 先调用 `InitializingBean.afterPropertiesSet()`
- 再执行 `@Bean(initMethod="xxx")`

#### **6.4 执行 `applyBeanPostProcessorsAfterInitialization()`**

- **这里是 AOP 代理创建的关键步骤！**

------

#### 6.5 **AOP 代理创建的关键细节**

Spring AOP 代理是在 `applyBeanPostProcessorsAfterInitialization()` 里完成的，具体是 **`AnnotationAwareAspectJAutoProxyCreator`** 这个 `BeanPostProcessor` 负责的。

##### **核心逻辑**

在 `AnnotationAwareAspectJAutoProxyCreator#postProcessAfterInitialization()` 方法：

```java
@Override
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    if (!this.advisedBeans.containsKey(cacheKey)) {
        if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
        } else {
            // **创建代理对象**
            Object proxy = createProxy(bean.getClass(), beanName, bean);
            return proxy;
        }
    }
    return bean;
}
```

##### **AOP 代理的关键点**

1. **`shouldSkip(bean.getClass(), beanName)`**
   - 判断当前 Bean 是否应该被 AOP 代理。
   - Spring 不会代理 `Advice`, `Advisor`, `AopInfrastructureBean` 等基础类。
2. **`createProxy(bean.getClass(), beanName, bean)`**
   - 这里会调用 `ProxyFactory` 来创建代理对象。

------

##### **Spring AOP 代理的两种方式**

1. **JDK 动态代理**（`java.lang.reflect.Proxy`）
   - 适用于实现了接口的 Bean。
   - **原理**：动态生成一个实现了目标接口的代理类，并拦截方法调用。
2. **CGLIB 动态代理**（`net.sf.cglib.proxy.Enhancer`）
   - 适用于没有实现接口的 Bean。
   - **原理**：创建目标类的子类，重写方法并拦截。

##### **AOP 代理的具体实现**

在 `AbstractAutoProxyCreator#createProxy()`：

```java
protected Object createProxy(Class<?> beanClass, String beanName, Object[] specificInterceptors, TargetSource targetSource) {
    ProxyFactory proxyFactory = new ProxyFactory();
    if (shouldUseCglibProxy(beanClass)) {
        proxyFactory.setProxyTargetClass(true); // 使用 CGLIB
    }
    proxyFactory.setTargetSource(targetSource);
    proxyFactory.addAdvisors(buildAdvisors(beanName, specificInterceptors));
    return proxyFactory.getProxy();
}
```

**关键点**：

- `shouldUseCglibProxy(beanClass)` 判断是否用 CGLIB 代理。
- `proxyFactory.addAdvisors()` 绑定切面逻辑。
- `proxyFactory.getProxy()` 生成代理对象。

------

### **7. 代理对象的返回**

当 `applyBeanPostProcessorsAfterInitialization()` 处理 AOP 时：

- 如果 Bean 需要 AOP，返回的是 **代理对象**。
- 如果不需要 AOP，返回的是原始对象。

最终 `getBean()` 取到的是代理对象。

------

## **完整的 Spring Bean 处理顺序**

| 阶段                 | 方法                                           | 关键点                                           |
| -------------------- | ---------------------------------------------- | ------------------------------------------------ |
| **1. 获取 Bean**     | `getBean()`                                    | 获取单例 Bean 或创建新 Bean                      |
| **2. 查找 Bean**     | `doGetBean()`                                  | 判断是否已创建，未创建则调用 `createBean()`      |
| **3. 创建 Bean**     | `createBean()`                                 | 反射实例化                                       |
| **4. 依赖注入**      | `populateBean()`                               | 处理 `@Autowired`                                |
| **5. 初始化 Bean**   | `initializeBean()`                             | 处理 Aware、`@PostConstruct`、`InitializingBean` |
| **6. AOP 代理**      | `applyBeanPostProcessorsAfterInitialization()` | **动态代理（JDK/CGLIB）**                        |
| **7. 返回最终 Bean** | -                                              | **可能是代理对象**                               |