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

### 1.1 判断web应用类型

```java
	this.webApplicationType = WebApplicationType.deduceFromClasspath();
	private static final String[] SERVLET_INDICATOR_CLASSES = { "javax.servlet.Servlet",
			"org.springframework.web.context.ConfigurableWebApplicationContext" };

	private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";

	private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";

	private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";

	static WebApplicationType deduceFromClasspath() {
        //判断是不是响应式的web类型
		if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null) && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
				&& !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS, null)) {
			return WebApplicationType.REACTIVE;
		}
        // 判断servlet类型所需要的类存不存在
		for (String className : SERVLET_INDICATOR_CLASSES) {
			if (!ClassUtils.isPresent(className, null)) {
				return WebApplicationType.NONE;
			}
		}
        // 如果Servlet和ConfigurableWebApplicationContext都存在，就表示是servlet类型
		return WebApplicationType.SERVLET;
	}
```

### 1.2 从SpringFactories获取实例

```java
	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes,
			ClassLoader classLoader, Object[] args, Set<String> names) {
		List<T> instances = new ArrayList<>(names.size());
		for (String name : names) {
			try {
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}


	private static Map<String, List<String>> loadSpringFactories(ClassLoader classLoader) {
		Map<String, List<String>> result = cache.get(classLoader);
		if (result != null) {
			return result;
		}

		result = new HashMap<>();
		try {
			Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				UrlResource resource = new UrlResource(url);
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					String factoryTypeName = ((String) entry.getKey()).trim();
					String[] factoryImplementationNames =
							StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
					for (String factoryImplementationName : factoryImplementationNames) {
						result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>())
								.add(factoryImplementationName.trim());
					}
				}
			}

			// Replace all lists with unmodifiable lists containing unique elements
			result.replaceAll((factoryType, implementations) -> implementations.stream().distinct()
					.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
			cache.put(classLoader, result);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
		return result;
	}
```

### 1.3 获取BootstrapRegistryInitializer实例

```java
0 = "org.springframework.cloud.bootstrap.TextEncryptorConfigBootstrapper"
```

### 1.4 获取ApplicationContextInitializer实例

```java
0 = "org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer"
1 = "org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener"
2 = "org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer"
3 = "org.springframework.boot.context.ContextIdApplicationContextInitializer"
4 = "org.springframework.boot.context.config.DelegatingApplicationContextInitializer"
5 = "org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer"
6 = "org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer"
```

### 1.5 获取ApplicationListener实例

```java
0 = "org.springframework.cloud.bootstrap.BootstrapApplicationListener"
1 = "org.springframework.cloud.bootstrap.LoggingSystemShutdownListener"
2 = "org.springframework.cloud.context.restart.RestartListener"
3 = "org.springframework.boot.autoconfigure.BackgroundPreinitializer"
4 = "org.springframework.boot.ClearCachesApplicationListener"
5 = "org.springframework.boot.builder.ParentContextCloserApplicationListener"
6 = "org.springframework.boot.context.FileEncodingApplicationListener"
7 = "org.springframework.boot.context.config.AnsiOutputApplicationListener"
8 = "org.springframework.boot.context.config.DelegatingApplicationListener"
9 = "org.springframework.boot.context.logging.LoggingApplicationListener"
10 = "org.springframework.boot.env.EnvironmentPostProcessorApplicationListener"
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
// 从cache中过滤出SpringApplicationRunListeners的结果集 => EventPublishingRunListener和NacosLoggingAppRunListener
// 反射创建EventPublishingRunListener对象的时候遍历之前设置的listeners然后添加到initialMulticaster的applicationListeners中
SpringApplicationRunListeners listeners = getRunListeners(args);
```

### 2.3 发布开始事件

```java
// EventPublishingRunListener的多播器发布ApplicationStartingEvent事件然后监听器根据自己的监听事件判断需不需要处理StartingEvent
// 比如LoggingApplicationListener开始实例化和初始化LoggingSystem;RestartApplicationListener开始实例化和初始化RestartInitializer
listeners.starting(bootstrapContext, this.mainApplicationClass);
```

#### 2.3.1 监听开始事件的监听器

```java
this.initialMulticaster.multicastEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
0 = {LoggingApplicationListener@1991}  //实例化日志系统
1 = {BackgroundPreinitializer@1992} 
2 = {DelegatingApplicationListener@1993} 
```

#### 2.3.2 实例化日志系统

```java
delegates size = 3
0 = {LogbackLoggingSystem$Factory@2141} 
1 = {Log4J2LoggingSystem$Factory@2142} 
2 = {JavaLoggingSystem$Factory@2143} 
// 判断是否引入logback的依赖
private static final boolean PRESENT = ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext",Factory.class.getClassLoader());
// 实例化LogbackLoggingSystem
public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
	if (PRESENT) {
		return new LogbackLoggingSystem(classLoader);
	}
	return null;
}
```

### 2.4 准备环境

```java
ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
// 准备环境，多播器发布ApplicationEnvironmentPreparedEvent事件 对应的监听器开始进行相关操作
ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
configureIgnoreBeanInfo(environment);
```

#### 2.4.1 创建环境

```java
// 创建可配置的环境对象
ConfigurableEnvironment environment = getOrCreateEnvironment();
// 当前项目web类型是servlet，根据web类型创建对应的环境对象
private <T> T getFromSpringFactories(WebApplicationType webApplicationType,
			BiFunction<ApplicationContextFactory, WebApplicationType, T> action, Supplier<T> defaultResult) {
	    for (ApplicationContextFactory candidate : SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class,
				getClass().getClassLoader())) {
		T result = action.apply(candidate, webApplicationType);
		if (result != null) {
			return result;
		}
	}
	return (defaultResult != null) ? defaultResult.get() : null;
}
// 创建可配置的servlet环境
new ApplicationServletEnvironment();
// 父类AbstractEnvironment实例化时添加PropertySources
protected AbstractEnvironment(MutablePropertySources propertySources) {
		this.propertySources = propertySources;
		this.propertyResolver = createPropertyResolver(propertySources);
		customizePropertySources(propertySources);
}
// StandardServletEnvironment
propertySourceList = {CopyOnWriteArrayList@3326}  size = 2
0 = {PropertySource$StubPropertySource@3328} "StubPropertySource {name='servletConfigInitParams'}"
1 = {PropertySource$StubPropertySource@3329} "StubPropertySource {name='servletContextInitParams'}"  
// StandardEnvironment
propertySourceList = {CopyOnWriteArrayList@3326}  size = 4
0 = {PropertySource$StubPropertySource@3328} "StubPropertySource {name='servletConfigInitParams'}"
1 = {PropertySource$StubPropertySource@3329} "StubPropertySource {name='servletContextInitParams'}"
2 = {PropertiesPropertySource@3356} "PropertiesPropertySource {name='systemProperties'}"
3 = {SystemEnvironmentPropertySource@3357} "SystemEnvironmentPropertySource {name='systemEnvironment'}"
```

#### 2.4.2 配置环境（可扩展）

```java
protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
         // 转换服务的设置
    	 // 在 application.properties 或 application.yml 中定义的字符串值自动转换为相应的 Java 类型（如 Duration、Enum、Path 等）。
         // 通过 @ConfigurationProperties 绑定属性时，自动进行数据格式转换。
		if (this.addConversionService) {
			environment.setConversionService(new ApplicationConversionService());
		}
   		 //属性源的配置
		configurePropertySources(environment, args);
   		// 配置 Profiles
		configureProfiles(environment, args);
}
```

#### 2.4.3 将属性源适配到Env

```java
ConfigurationPropertySources.attach(environment);
public static void attach(Environment environment) {
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment);
		MutablePropertySources sources = ((ConfigurableEnvironment) environment).getPropertySources();
		PropertySource<?> attached = getAttached(sources);
		if (attached == null || !isUsingSources(attached, sources)) {
			attached = new ConfigurationPropertySourcesPropertySource(ATTACHED_PROPERTY_SOURCE_NAME,
					new SpringConfigurationPropertySources(sources));
		}
		sources.remove(ATTACHED_PROPERTY_SOURCE_NAME);
		sources.addFirst(attached);
	}
```

#### 2.4.4 发布环境准备事件

```java
listeners.environmentPrepared(bootstrapContext, environment);
this.initialMulticaster.multicastEvent(new ApplicationEnvironmentPreparedEvent(bootstrapContext, this.application, this.args, environment));
0 = {BootstrapApplicationListener@2492} 
1 = {LoggingSystemShutdownListener@2493} 
2 = {EnvironmentPostProcessorApplicationListener@2494} 
3 = {AnsiOutputApplicationListener@2495} 
4 = {LoggingApplicationListener@1958} 
5 = {BackgroundPreinitializer@2138} 
6 = {DelegatingApplicationListener@2147} 
7 = {FileEncodingApplicationListener@2496} 
```

##### 2.4.4.1 BootstrapApplicationListener（springcloud-context）

```java
//cloud项目会引入相关依赖，在环境准备时创建新的环境和上下文对象，重新 执行run方法，加载配置文件，然后合并属性源，添加初始化器
initializers = {LinkedHashSet@5029}  size = 10
 0 = {BootstrapApplicationListener$AncestorInitializer@5034} 
 1 = {SharedMetadataReaderFactoryContextInitializer@5035} 
 2 = {DelegatingApplicationContextInitializer@5036} 
 3 = {ContextIdApplicationContextInitializer@5037} 
 4 = {ConditionEvaluationReportLoggingListener@5038} 
 5 = {ConfigurationWarningsApplicationContextInitializer@5039} 
 6 = {RSocketPortInfoApplicationContextInitializer@5040} 
 7 = {ServerPortInfoApplicationContextInitializer@5041} 
 8 = {PropertySourceBootstrapConfiguration@5042} 
 9 = {EnvironmentDecryptApplicationInitializer@5043} 
//后续的监听器再执行
```

##### 2.4.4.2 LoggingSystemShutdownListener（springcloud-context）

```
springcloud项目会引入相关依赖，在启动时创建 bootstrap 上下文后立即清理日志记录系统
```

##### 2.4.4.3 EnvironmentPostProcessorApplicationListener

```java
EnvironmentPostProcessors = {Collections$UnmodifiableRandomAccessList@2317}  size = 7
// 添加随机数属性源
 0 = {RandomValuePropertySourceEnvironmentPostProcessor@2319} 
// 替换systemEvn属性源为OriginAwareSystemEnvironmentPropertySource，主要作用是 在解析系统环境变量时，保留属性的来源信息（Origin）
 1 = {SystemEnvironmentPropertySourceEnvironmentPostProcessor@2320} 
// 解析SPRING_APPLICATION_JSON 环境变量或系统属性，并将其添加到Environment 中
// java -Dspring.application.json='{"server.port":8081, "spring.datasource.url":"jdbc:mysql://localhost:3306/test"}' -jar myapp.jar
 2 = {SpringApplicationJsonEnvironmentPostProcessor@2321} 
 3 = {CloudFoundryVcapEnvironmentPostProcessor@2322} 
// 读取properties和yml配置文件，添加属性源到Evn，代替了之前的ConfigFileApplicationListener
 4 = {ConfigDataEnvironmentPostProcessor@2323} 
 5 = {DebugAgentEnvironmentPostProcessor@2324} 
// 读取META-INF/spring.integration.properties路径下的配置文件，添加属性源到Evn
 6 = {IntegrationPropertiesEnvironmentPostProcessor@2325} 
```

#### 2.4.4.4 LoggingApplicationListener

```java
// 初始化日志系统,读取类路径下的logback配置文件，解析xml，填充对象属性完成日志系统的初始化
protected void initialize(ConfigurableEnvironment environment, ClassLoader classLoader) {
	getLoggingSystemProperties(environment).apply();
	this.logFile = LogFile.get(environment);
	if (this.logFile != null) {
		this.logFile.applyToSystemProperties();
	}
	this.loggerGroups = new LoggerGroups(DEFAULT_GROUP_LOGGERS);
	initializeEarlyLoggingLevel(environment);
	initializeSystem(environment, this.loggingSystem, this.logFile);
	initializeFinalLoggingLevels(environment, this.loggingSystem);
	registerShutdownHookIfNecessary(environment, this.loggingSystem);
}
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

#### 2.6.1 创建上下文

```java
// 和之前创建Evn的流程一样，根据web应用类型创建AnnotationConfigServletWebServerApplicationContext
protected ConfigurableApplicationContext createApplicationContext() {
		return this.applicationContextFactory.create(this.webApplicationType);
}
//在构造器里实例化两个对象
public AnnotationConfigServletWebServerApplicationContext() {
	this.reader = new AnnotatedBeanDefinitionReader(this);
	this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```

#### 2.6.2 AnnotatedBeanDefinitionReader

```java
// BeanDefinitionRegistry：用于注册bean定义信息的接口，ioc容器实现此接口，上下文也实现此接口。
// BeanDefinitionReader： 用于读取bean定义信息的接口，持有一个BeanDefinitionRegistry，有两个实现类Xml和properties用于读取两种配置的bean定义信息
// ClassPathBeanDefinitionScanner：用于扫描类路径下bean定义信息的类，持有一个BeanDefinitionRegistry，扫描完成后使用Registry注册BeanDefinition到ioc容器
// 一个特殊的BeanDefinitionReader，没有实现BeanDefinitionReader接口，在构造器里面通过	    AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);注册了五个bean定义信息
beanDefinitionNames = {ArrayList@3501}  size = 5
  0 = "org.springframework.context.annotation.internalConfigurationAnnotationProcessor"
  1 = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"
  2 = "org.springframework.context.annotation.internalCommonAnnotationProcessor"
  3 = "org.springframework.context.event.internalEventListenerProcessor"
  4 = "org.springframework.context.event.internalEventListenerFactory"
```

#### 2.6.3 创建ioc容器

```java
// 在父类GenericApplicationContext的构造方法里创建ioc容器DefaultListableBeanFactory
// 所以，ApplicationContext和DefaultListableBeanFactory同属于BeanFactory的子类，ApplicationContext持有唯一一个DefaultListableBeanFactory
public GenericApplicationContext() {
    // 查看父类AbstractAutowireCapableBeanFactory的构造方法
	this.beanFactory = new DefaultListableBeanFactory();
}
```

#### 2.6.4 AbstractAutowireCapableBeanFactory

```java
    /**这些 Aware 接口 允许 Bean 在初始化时获得额外的 Spring 容器信息，比如：
     * BeanNameAware —— 让 Bean 知道自己的 Bean 名称。
     * BeanFactoryAware —— 让 Bean 获得 BeanFactory。
     * BeanClassLoaderAware —— 让 Bean 获取 ClassLoader。
     * Spring 默认情况下 不把这些接口作为普通的依赖注入，而是由 Spring 容器在 setBeanName、setBeanFactory 等方法中显式回调，所以不需要进行 自动依赖注入。
     * 这样可以避免 Bean 在依赖注入阶段把 BeanFactory 误当成普通 Bean 依赖
     */
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
         /**
         * 选择 Bean 的 实例化策略。
         * NativeDetector.inNativeImage() 作用
         * 判断当前是否运行在 GraalVM 的 Native Image 中。
         * GraalVM 不支持 CGLIB（因为它需要动态代理），所以如果是在 Native Image 里，就使用 简单实例化策略。
         * SimpleInstantiationStrategy
         *
         * 直接使用 Java 反射 API (Constructor.newInstance()) 创建对象。
         * 适用于 GraalVM Native Image，或者 Spring 配置 proxyBeanMethods = false 时。
         * CglibSubclassingInstantiationStrategy
         *
         * 默认策略，如果不是 GraalVM，就使用 CGLIB 生成子类 进行实例化。
         * 作用：可以支持 方法拦截、动态代理，Spring AOP 依赖它。
         */
		if (NativeDetector.inNativeImage()) {
			this.instantiationStrategy = new SimpleInstantiationStrategy();
		}
		else {
			this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
		}
	}
```

### 2.7 上下文刷新前准备

```java
// ioc容器刷新前的准备工作
prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
```

#### 2.7.1 设置上下文环境

```java
    // 创建上下文的过程中在AnnotatedBeanDefinitionReader构造方法里调用getOrCreateEnvironment方法，创建了新的环境
    // 所以将之前配置好的Evn覆盖到上下文未配置的环境
    context.setEnvironment(environment);
```

#### 2.7.2 后置处理相关的上下文配置（可扩展）

```java
    // 重写此方法自定义上下文后置处理程序
    postProcessApplicationContext(context);
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        
		if (this.beanNameGenerator != null) {
            // 注册之前创建上下文时加载的bean定义信息internalConfigurationAnnotationProcessor
			context.getBeanFactory()
				.registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
            // 设置资源和类加载器
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context).setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context).setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		if (this.addConversionService) {
            // 将之前在环境配置时的属性转换器设置给ioc
			context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
		}
	}
```

#### 2.7.3 应用springboot初始化器（**可扩展**）

```java
// 将之前在构造方法里加载的初始化器使用，ApplicationContextInitializer的实现类
applyInitializers(context);
// 这个初始化器加载naocs配置文件，他是在发布环境准备事件时，引导类监听在apply时添加的
PropertySourceBootstrapConfiguration
NacosPropertySourceLocator
```

#### 2.7.4 发布上下文初始化完成事件

```java
//发布ApplicationContextInitializedEvent
listeners.contextPrepared(context);
this.initialMulticaster.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
结果 = {ArrayList@3339}  size = 2
 0 = {BackgroundPreinitializer@3341} 
 1 = {DelegatingApplicationListener@3342} 
```

#### 2.7.5 关闭引导上下文

```java
// 发布引导上下文关闭事件，
bootstrapContext.close(context);
```

#### 2.7.6 注册一些特殊的bean用于引导启动程序继续运行

```java
		
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// 通过DefaultSingletonBeanRegistry注册单例bean，applicationArguments和printedBanner都是之前创建完成的对象，
		// 可以直接注册到Map<String, Object> singletonObjects(一级缓存)
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof AbstractAutowireCapableBeanFactory) {
			((AbstractAutowireCapableBeanFactory) beanFactory).setAllowCircularReferences(this.allowCircularReferences);
			if (beanFactory instanceof DefaultListableBeanFactory) {
                // 设置是否可以注册相同名称的bean，如果可以则替换，如果不可以则报错
				((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
			}
		}
```

#### 2.7.7 添加bean工厂后置处理器

```java
		// 让Spring 以懒加载（lazy initialization）方式初始化 Bean，提高应用启动速度。
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// 确保 Spring 配置属性的加载顺序正确
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
```

#### 2.7.8 加载上下文

```java
	    // 加载并注册primarySources的BeanDefinition
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
        load(context, sources.toArray(new Object[0]));
```

#### 2.7.9 发布应用准备完成事件

```java
// 发布ApplicationPreparedEvent
listeners.contextLoaded(context);
结果 = {ArrayList@3557}  size = 4
// 打印启动过程中延迟的日志
 0 = {EnvironmentPostProcessorApplicationListener@3559} 
// 注册单例对象，都是之前创建的关于日志的对象
 1 = {LoggingApplicationListener@3560} 
 2 = {BackgroundPreinitializer@3341} 
 3 = {DelegatingApplicationListener@3342} 
```

### 2.8 刷新上下文

```java
    // *刷新上下文
    refreshContext(context);
	
	private void refreshContext(ConfigurableApplicationContext context) {
        	// 注册上下文关闭钩子函数
		if (this.registerShutdownHook) {
			shutdownHook.registerApplicationContext(context);
		}
		refresh(context);
	}
```

#### 2.8.1 创建启动步骤

```java
    // 标记上下文刷新开始
    // 用于收集应用启动过程中的性能数据，帮助分析 refresh() 过程中各个阶段的耗时情况。
    // 使用 BufferingApplicationStartup（适合开发调试,步骤分析）
 	// new SpringApplication(xxx.class).setApplicationStartup(new BufferingApplicationStartup(1000));
    // 使用 FlightRecorderApplicationStartup（适合生产环境）
    StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");
```

#### 2.8.2 准备刷新

```java
    // Prepare this context for refreshing.
    prepareRefresh();
```

##### 2.8.2.1 AnnotationConfigServletWebServerApplicationContext

```java
	protected void prepareRefresh() {
		// 删除ClassPathBeanDefinitionScanner缓存的元数据
		this.scanner.clearCache();
		super.prepareRefresh();
	}
```

##### 2.8.2.2 AbstractApplicationContext

```java
    protected void prepareRefresh() {
		...
		// Initialize any placeholder property sources in the context environment.
            // 在GenericWebApplicationContext中servelet上下文还没创建，所以不能进行PropertySources占位符的替换
            // 后续会在onRefresh()方法执行时创建servlet上下文,再次替换PropertySources的占位符
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
        // 确保 refresh() 过程中不会丢失早期监听器（比如 ApplicationListener 被动态修改时）
        // 当 refresh() 失败时，可以恢复 applicationListeners 到原始状态。
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			// Reset local application listeners to pre-refresh state.
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
        // 存储 Spring 容器初始化早期触发的事件（ApplicationEvent）,这些事件不能立刻广播，
        // 因为此时 ApplicationEventMulticaster（事件广播器）还没有初始化,等 initApplicationEventMulticaster() 执行完，再统一发布
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}
```

#### 2.8.3 获取新鲜的bean工厂

```java
// 之前在创建上下文的时候已经创建过bean工厂，现在刷新bean工厂，设置工厂id，然后返回bean工厂
ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
```

#### 2.8.4 准备bean工厂

```java
// 设置ioc容器关键属性
// 注册关键的BeanPostProcessor
prepareBeanFactory(beanFactory);
```

##### 2.8.4.1 配置 BeanFactory 的基本属性

```java
// 设置 BeanFactory 使用 ApplicationContext 的类加载器
// 让 BeanFactory 使用 ApplicationContext 的类加载器，确保能正确加载类（如 @Component 扫描的 Bean）。
beanFactory.setBeanClassLoader(getClassLoader());

// 如果 SpEL 允许，则设置 Bean 表达式解析器
// 让 @Value("#{systemProperties['user.name']}") 这种 SpEL 语法生效。
// 仅当 shouldIgnoreSpel == false 时启用。
if (!shouldIgnoreSpel) {
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
}

// 添加 PropertyEditorRegistrar，解析 `@Value("${}")` 这样的属性
beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

```

##### 2.8.4.2 处理 Aware回调接口

```java
// 添加 ApplicationContext 相关的 BeanPostProcessor
// 处理实现了 ApplicationContextAware、ResourceLoaderAware、EnvironmentAware 等接口的 Bean，使其能获取 ApplicationContext 的信息。
beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

// 忽略某些依赖接口，防止自动注入
// Spring 规定：ignoreDependencyInterface 的接口不会通过 @Autowired 自动注入，而是由 ApplicationContext 处理。
// 例如，ApplicationContextAware 不会通过 @Autowired 注入，而是由 ApplicationContextAwareProcessor 处理。
beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

```

##### 2.8.4.3 注册 ResolvableDependency（可解析依赖项）

```java
// 允许在 Bean 中 `@Autowired` 注入这些类型
beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory); // 允许 Bean 直接注入 BeanFactory
beanFactory.registerResolvableDependency(ResourceLoader.class, this); // ApplicationContext 也是 ResourceLoader，可以加载资源。
beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);// 让 Bean 可以发布事件
beanFactory.registerResolvableDependency(ApplicationContext.class, this);// 让 Bean 可以直接 @Autowired 注入 ApplicationContext获取上下文信息

```

##### 2.8.4.4 处理ApplicationListener和 LoadTimeWeaver

```java
// 添加监听器探测器
// 负责检测 ApplicationListener 类型的 Bean，在 refresh() 时自动注册为事件监听器。
beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

// 处理 LoadTimeWeaver
// 如果 Spring 发现 LoadTimeWeaver（JVM 运行时增强机制），它会注册 LoadTimeWeaverAwareProcessor 来处理类加载时的字节码增强。
if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
    beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
    beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
}

```

##### 2.8.4.5 注册环境相关的默认 Bean

```java
// 注册 `environment` Bean
if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
    beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
}

// 注册 `systemProperties` Bean
if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
    beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
}

// 注册 `systemEnvironment` Bean
if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
    beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
}

// 注册 `applicationStartup` Bean
if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
    beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
}

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