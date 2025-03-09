# Spring Boot 启动流程

## 1. 初始化 SpringApplication

```java
@SuppressWarnings({ "unchecked", "rawtypes" })
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
	this.resourceLoader = resourceLoader;
	Assert.notNull(primarySources, "PrimarySources must not be null");
	this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
	this.webApplicationType = WebApplicationType.deduceFromClasspath();
	// loadSpringFactories方法从cache中查找启动类的类加载器(AppClassloader)对应的结果集，当前cache为空，
	// 去META-INF/spring.factories路径下加载，放入cache，
	// 过滤出Key(BootstrapRegistryInitializer)对应的结果集value(names)并反射创建对象集合(instances)然后返回
	this.bootstrapRegistryInitializers = new ArrayList<>(getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
	//加载设置初始化器 => cache不为空，过滤出ApplicationContextInitializer对应的结果集并反射创建对象然后返回
	setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
	//加载设置监听器 => cache不为空，过滤出ApplicationListener对应的结果集并反射创建对象然后返回
	setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
    // 判断main方法所在的启动类
	this.mainApplicationClass = deduceMainApplicationClass();
}	
```

## 2. 调用 run 方法

### 2.1 创建BootstrapContext

```java
long startTime = System.nanoTime();
// springcloud项目里会引入BootstrapRegistryInitializer的实现类，初始化的时候加载了相关的实现类后
// 遍历所有的 BootstrapRegistryInitializer 初始化器，并对每个初始化器执行初始化操作
// 给bootstrapContext添加两个监听器ApplicationListener<BootstrapContextClosedEvent>
// 返回初始化后的 bootstrapContext，使得 Spring Boot 启动过程中的各个组件和配置能够在该上下文中找到合适的资源。
DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
```



### 2.2 获取监听器

```java
// 从cache中过滤出SpringApplicationRunListeners的结果集 => EventPublishingRunListener
// 反射创建EventPublishingRunListener对象的时候遍历之前设置的listeners然后添加到initialMulticaster的applicationListeners中
SpringApplicationRunListeners listeners = getRunListeners(args);
```



### 2.3 发布开始事件

```java
// EventPublishingRunListener的多播器发布ApplicationStartingEvent事件然后监听器根据自己的监听事件判断需不需要处理StartingEvent
// 比如LoggingApplicationListener开始实例化和初始化LoggingSystem;RestartApplicationListener开始实例化和初始化RestartInitializer
listeners.starting(bootstrapContext, this.mainApplicationClass);
```



### 2.4 发布准备环境事件

```java
ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
// 准备环境，多播器发布ApplicationEnvironmentPreparedEvent事件 对应的监听器开始进行相关操作
ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
configureIgnoreBeanInfo(environment);
```



### 2.5 输出banner

```java
// 打印banner
Banner printedBanner = printBanner(environment);
```



### 2.6 创建上下文和ioc容器

```java
// 创建ioc容器 有注解配置形式有xml配置形式的 有响应式的有servlet的
// 父类GenericApplicationContext构造器初始化this.beanFactory = new DefaultListableBeanFactory();
context = createApplicationContext();
// 设置容器启动指标以在启动执行器端点和Java Flight Recorder中进行监视的快速指南
context.setApplicationStartup(this.applicationStartup);
```



### 2.7 ioc容器刷新前的准备工作（发布上下文准备事件和上下文加载事件）

```java
// ioc容器刷新前的准备工作
prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
```



### 2.8 刷新上下文

```java
// *刷新上下文
refreshContext(context);
```

### 2.9 刷新后执行

```java
// 在上下文被刷新后调用，留给子项目扩展
afterRefresh(context, applicationArguments);
```

### 2.10 计算启动时间

```java
// 计算应用启动时间
Duration timeTakenToStartup = Duration.ofNanos(System.nanoTime() - startTime);
if (this.logStartupInfo) {
	new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), timeTakenToStartup);
}
```

### 2.11 发布启动完成事件

```java
// 启动完成，多播器发布ApplicationStartedEvent事件
listeners.started(context, timeTakenToStartup);
```

### 2.12 调用扩展的运行程序

```java
// 在项目启动时可执行操作的扩展方式
callRunners(context, applicationArguments);
```

