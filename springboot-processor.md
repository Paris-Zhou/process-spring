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
// 一个特殊的BeanDefinitionReader，没有实现BeanDefinitionReader接口，在构造器里面通过	    
// AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);注册了五个bean定义信息
// ConfigurationClassPostProcessor实现了BeanDefinitionRegistryPostProcessor接口，
// 会在刷新上下文时，执行invokeBeanFactoryPostProcessors(beanFactory);注册bean定义信息
// myabtis的@MapperScan也会注册一个MapperScannerConfigurer类的bean定义信息，它也实现了BeanDefinitionRegistryPostProcessor接口，扫描包路径下的所有mapper
// 如果引入springdata-jpa依赖，还会注册"org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor"
beanDefinitionNames = {ArrayList@3501}  size = 5
  0 = "org.springframework.context.annotation.internalConfigurationAnnotationProcessor" ConfigurationClassPostProcessor
  1 = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor" AutowiredAnnotationBeanPostProcessor
  2 = "org.springframework.context.annotation.internalCommonAnnotationProcessor" CommonAnnotationBeanPostProcessor  @PostConstruct @PreDestroy
  3 = "org.springframework.context.event.internalEventListenerProcessor" EventListenerMethodProcessor
  4 = "org.springframework.context.event.internalEventListenerFactory" DefaultEventListenerFactory
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

// 添加 PropertyEditorRegistrar，将字符串路径转换为 Resource 对象, 使用@Value("classpath:application.yml")注入资源
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

#### 2.8.5 对bean工厂后置处理

```java
	// 允许在 context 子类中对 bean 工厂进行后处理。
	postProcessBeanFactory(beanFactory);
```

##### 2.8.5.1 AnnotationConfigServletWebServerApplicationContext

```java
@Override
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 调用父类的 postProcessBeanFactory 方法，确保父类的 BeanFactory 处理逻辑仍然执行
    super.postProcessBeanFactory(beanFactory);

    // 如果 basePackages（基础包路径）不为空，则使用 scanner（通常是 ClassPathBeanDefinitionScanner）
    // 进行包扫描，以注册符合条件的 Spring 组件（如 @Component、@Service、@Repository、@Controller）
    if (this.basePackages != null && this.basePackages.length > 0) {
        this.scanner.scan(this.basePackages);
    }

    // 如果 annotatedClasses（指定的注解类）不为空，则使用 reader（通常是 AnnotatedBeanDefinitionReader）
    // 直接注册这些类到 BeanFactory，使其成为 Spring 管理的 Bean
    if (!this.annotatedClasses.isEmpty()) {
        this.reader.register(ClassUtils.toClassArray(this.annotatedClasses));
    }
}

```

##### 2.8.5.2 ServletWebServerApplicationContext

```java
@Override
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 向 BeanFactory 中添加一个 BeanPostProcessor：
    // WebApplicationContextServletContextAwareProcessor 负责在 Bean 初始化时
    // 将 ServletContext 和 ServletConfig 注入到实现了 ServletContextAware 或 ServletConfigAware 接口的 Bean 中。
    beanFactory.addBeanPostProcessor(new WebApplicationContextServletContextAwareProcessor(this));

    // 告诉 Spring 在自动装配时忽略 ServletContextAware 接口，
    // 因为这些 Bean 会通过 WebApplicationContextServletContextAwareProcessor 手动注入 ServletContext。
    beanFactory.ignoreDependencyInterface(ServletContextAware.class);

    // 注册 Web 作用域（scopes），例如 request、session 和 application 作用域，
    // 使得 Spring 可以在 Web 环境中管理 Bean 的生命周期（如 request-scope Bean 每个请求创建一个实例）。
    registerWebApplicationScopes();
}

```

#### 2.8.6 调用bean工厂后置处理器

```java
// 调用在上下文中注册为 bean 的工厂处理器。
invokeBeanFactoryPostProcessors(beanFactory);

/**
 * 调用 BeanFactoryPostProcessor 进行处理，并在非 Native Image 环境下，
 * 如果存在 LoadTimeWeaver，则进行相应的类加载器设置。
 *
 * @param beanFactory 可配置的 Bean 工厂
 */
protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
    // 调用 Spring 内部工具类 PostProcessorRegistrationDelegate 来执行 BeanFactoryPostProcessor 逻辑
    // 这些后处理器可以在 BeanFactory 初始化阶段修改 BeanDefinition
    PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

    // 如果当前不是 GraalVM Native Image 环境，并且 BeanFactory 没有临时类加载器，
    // 同时存在名为 "loadTimeWeaver" 的 Bean，则进行 LoadTimeWeaver 相关的处理
    if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null &&
            beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {

        // 向 BeanFactory 添加 LoadTimeWeaverAwareProcessor，这个 Processor 负责
        // 让所有实现了 LoadTimeWeaverAware 接口的 Bean 能够获得 LoadTimeWeaver 实例
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));

        // 设置临时类加载器 ContextTypeMatchClassLoader，它用于处理类加载匹配，增强 AOP 和其他动态代理功能
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }
}

```

##### 2.8.6.1 PostProcessorRegistrationDelegate

```java
/**
 * 调用所有注册的 BeanFactoryPostProcessor 以修改 BeanFactory 配置。
 * 该方法按照优先级（PriorityOrdered、Ordered、无序）依次执行。
 */
public static void invokeBeanFactoryPostProcessors(
        ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

    // 记录已处理的 BeanFactoryPostProcessor，避免重复执行
    Set<String> processedBeans = new HashSet<>();

    // 如果 BeanFactory 是 BeanDefinitionRegistry（通常是 DefaultListableBeanFactory），则先处理 BeanDefinitionRegistryPostProcessor
    if (beanFactory instanceof BeanDefinitionRegistry) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
        List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
     
        // 遍历手动注册的 BeanFactoryPostProcessor
        for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
            if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                // 如果是 BeanDefinitionRegistryPostProcessor，则先执行其 postProcessBeanDefinitionRegistry 方法
                BeanDefinitionRegistryPostProcessor registryProcessor = 
                        (BeanDefinitionRegistryPostProcessor) postProcessor;
                registryProcessor.postProcessBeanDefinitionRegistry(registry);
                registryProcessors.add(registryProcessor);
            } else {
                // 否则是普通的 BeanFactoryPostProcessor，暂存等待后续执行
                regularPostProcessors.add(postProcessor);
            }
        }

        // 处理 BeanDefinitionRegistryPostProcessor
        List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

        // 1. 先执行实现了 PriorityOrdered 的 BeanDefinitionRegistryPostProcessor
        String[] postProcessorNames = 
                beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                processedBeans.add(ppName);
            }
        }
        sortPostProcessors(currentRegistryProcessors, beanFactory);
        registryProcessors.addAll(currentRegistryProcessors);
        // ConfigurationClassPostProcessor实现了PriorityOrdered
        invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
        currentRegistryProcessors.clear();

        // 2. 再执行实现了 Ordered 的 BeanDefinitionRegistryPostProcessor
        postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
        for (String ppName : postProcessorNames) {
            if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                processedBeans.add(ppName);
            }
        }
        sortPostProcessors(currentRegistryProcessors, beanFactory);
        registryProcessors.addAll(currentRegistryProcessors);
        invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
        currentRegistryProcessors.clear();

        // 3. 最后执行所有剩余的 BeanDefinitionRegistryPostProcessor，直到没有新的出现
        boolean reiterate = true;
        while (reiterate) {
            reiterate = false;
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                    reiterate = true;
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
            currentRegistryProcessors.clear();
        }

        // 处理完所有 BeanDefinitionRegistryPostProcessor 后，执行其 postProcessBeanFactory 方法
        invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
    } else {
        // 如果 BeanFactory 不是 BeanDefinitionRegistry（例如普通 BeanFactory），则直接处理 BeanFactoryPostProcessor
        invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
    }

    // 获取所有 BeanFactoryPostProcessor（不包括 BeanDefinitionRegistryPostProcessor）
    String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

    // 分类处理 BeanFactoryPostProcessor（PriorityOrdered、Ordered、无序）
    List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
    List<String> orderedPostProcessorNames = new ArrayList<>();
    List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    for (String ppName : postProcessorNames) {
        if (processedBeans.contains(ppName)) {
            // 已处理的跳过
        } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
            priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
        } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
            orderedPostProcessorNames.add(ppName);
        } else {
            nonOrderedPostProcessorNames.add(ppName);
        }
    }

    // 1. 先执行 PriorityOrdered 的 BeanFactoryPostProcessor
    sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

    // 2. 再执行 Ordered 的 BeanFactoryPostProcessor
    List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
    for (String postProcessorName : orderedPostProcessorNames) {
        orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    sortPostProcessors(orderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

    // 3. 最后执行无序的 BeanFactoryPostProcessor
    List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
    for (String postProcessorName : nonOrderedPostProcessorNames) {
        nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

    // 清理 BeanFactory 的元数据缓存，确保后处理器可能修改的元数据可以正确应用
    beanFactory.clearMetadataCache();
}

```

##### 2.8.6.2 ConfigurationClassPostProcessor

```java
/**
 * 处理 Spring 配置类的 BeanDefinition，主要解析 @Configuration 标注的类，
 * 并注册相应的 BeanDefinition。
 *
 * @param registry BeanDefinitionRegistry，Spring 容器中所有 BeanDefinition 的注册中心
 */
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    // 用于存储符合 @Configuration 条件的 BeanDefinitionHolder
    List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
    // 获取所有已注册的 BeanDefinition 名称
    String[] candidateNames = registry.getBeanDefinitionNames();

    // 遍历所有 BeanDefinition，寻找符合条件的 @Configuration 类
    for (String beanName : candidateNames) {
        BeanDefinition beanDef = registry.getBeanDefinition(beanName);
        
        // 如果 BeanDefinition 已经被标记为配置类，则跳过
        if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
            }
        }
        // 检查当前 BeanDefinition 是否是一个合法的配置类（@Configuration/@Component 等）
        else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
            configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
        }
    }

    // 如果没有找到 @Configuration 类，直接返回
    if (configCandidates.isEmpty()) {
        return;
    }

    // 按照 @Order 注解的值对配置类进行排序（优先级高的先处理）
    configCandidates.sort((bd1, bd2) -> {
        int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
        int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
        return Integer.compare(i1, i2);
    });

    // 检测是否有自定义的 BeanNameGenerator 以生成 Bean 名称
    SingletonBeanRegistry sbr = null;
    if (registry instanceof SingletonBeanRegistry) {
        sbr = (SingletonBeanRegistry) registry;
        if (!this.localBeanNameGeneratorSet) {
            BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(
                    AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
            if (generator != null) {
                this.componentScanBeanNameGenerator = generator;
                this.importBeanNameGenerator = generator;
            }
        }
    }

    // 如果环境变量为空，则初始化 StandardEnvironment
    if (this.environment == null) {
        this.environment = new StandardEnvironment();
    }

    // 创建 ConfigurationClassParser 解析配置类
    ConfigurationClassParser parser = new ConfigurationClassParser(
            this.metadataReaderFactory, this.problemReporter, this.environment,
            this.resourceLoader, this.componentScanBeanNameGenerator, registry);

    Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
    Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
    do {
        // 记录解析过程的性能分析（Spring 启动分析）
        StartupStep processConfig = this.applicationStartup.start("spring.context.config-classes.parse");
        parser.parse(candidates);
        parser.validate();

        // 获取解析后的 ConfigurationClass
        Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
        configClasses.removeAll(alreadyParsed);

        // 将解析出的 ConfigurationClass 转换为 BeanDefinition 并注册到容器中
        if (this.reader == null) {
            this.reader = new ConfigurationClassBeanDefinitionReader(
                    registry, this.sourceExtractor, this.resourceLoader, this.environment,
                    this.importBeanNameGenerator, parser.getImportRegistry());
        }
        this.reader.loadBeanDefinitions(configClasses);
        alreadyParsed.addAll(configClasses);
        processConfig.tag("classCount", () -> String.valueOf(configClasses.size())).end();

        // 处理新解析出来的 BeanDefinition，防止遗漏
        candidates.clear();
        if (registry.getBeanDefinitionCount() > candidateNames.length) {
            String[] newCandidateNames = registry.getBeanDefinitionNames();
            Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
            Set<String> alreadyParsedClasses = new HashSet<>();
            for (ConfigurationClass configurationClass : alreadyParsed) {
                alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
            }
            for (String candidateName : newCandidateNames) {
                if (!oldCandidateNames.contains(candidateName)) {
                    BeanDefinition bd = registry.getBeanDefinition(candidateName);
                    if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
                            !alreadyParsedClasses.contains(bd.getBeanClassName())) {
                        candidates.add(new BeanDefinitionHolder(bd, candidateName));
                    }
                }
            }
            candidateNames = newCandidateNames;
        }
    }
    while (!candidates.isEmpty());

    // 将 ImportRegistry 注册为单例 Bean，支持 ImportAware 机制
    if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
        sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
    }

    // 清理 MetadataReaderFactory 缓存，确保不会占用过多资源
    if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
        ((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
    }
}

```



##### 2.8.6.3 ConfigurationClassParser

```java
public void parse(Set<BeanDefinitionHolder> configCandidates) {
    // 遍历所有候选的 BeanDefinitionHolder
    for (BeanDefinitionHolder holder : configCandidates) {
        BeanDefinition bd = holder.getBeanDefinition();
        try {
            // 如果 BeanDefinition 是 AnnotatedBeanDefinition（基于注解的 Bean 定义）
            if (bd instanceof AnnotatedBeanDefinition) {
                // 解析该 Bean 的元数据（AnnotationMetadata），并传入 Bean 名称
                parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
            }
            // 如果 BeanDefinition 是 AbstractBeanDefinition 且已经解析出 BeanClass
            else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
                // 直接解析该 Bean 的 Class 类型
                parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
            }
            // 其他情况（可能是 XML 配置的 Bean），使用类名进行解析
            else {
                parse(bd.getBeanClassName(), holder.getBeanName());
            }
        }
        // 捕获 BeanDefinition 解析异常并重新抛出
        catch (BeanDefinitionStoreException ex) {
            throw ex;
        }
        // 捕获其他异常，并包装为 BeanDefinitionStoreException 进行抛出
        catch (Throwable ex) {
            throw new BeanDefinitionStoreException(
                    "Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
        }
    }

    // 处理所有延迟导入（deferred import selectors），主要是 @Import 注解涉及的 ImportSelector
    this.deferredImportSelectorHandler.process();
}

```

```java
// 解析基于类名的配置类
protected final void parse(@Nullable String className, String beanName) throws IOException {
    // 确保 className 不能为空
    Assert.notNull(className, "No bean class name for configuration class bean definition");

    // 通过 MetadataReaderFactory 获取 MetadataReader，用于读取类的元信息
    MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);

    // 解析配置类，传入 MetadataReader 读取到的类信息
    processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
}

// 解析基于 Class<?> 对象的配置类
protected final void parse(Class<?> clazz, String beanName) throws IOException {
    // 直接使用 Class 对象构造 ConfigurationClass 并进行处理
    processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
}

// 解析基于 AnnotationMetadata（注解元数据）的配置类
protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
    // 直接使用 AnnotationMetadata 构造 ConfigurationClass 并进行处理
    processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
}

```

```java
/**
详细解读
1. 判断该配置类是否应该跳过解析
if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
    return;
}
this.conditionEvaluator.shouldSkip(...) 用于检查该类是否需要跳过解析：
主要检查 @Conditional 注解，如果条件不满足，就不会解析该类。
该方法的第二个参数 ConfigurationPhase.PARSE_CONFIGURATION 代表当前解析阶段。
2. 处理重复解析的配置类
ConfigurationClass existingClass = this.configurationClasses.get(configClass);
先检查当前类是否已经被解析过，如果已经解析过，需要做以下处理：
如果该类是 @Import 进来的：

如果已经存在一个通过 @Import 进来的相同类，则合并 @Import 信息。
如果原来有一个非 @Import 的显式定义的配置类，则优先保留已有的，忽略新的 @Import 进来的类。
如果该类是显式定义的 Bean：

说明它可能是一个新的 Bean 定义覆盖了之前的 @Import 定义，需要移除旧的配置类。
3. 递归解析配置类
SourceClass sourceClass = asSourceClass(configClass, filter);
do {
    sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
} while (sourceClass != null);
asSourceClass(configClass, filter)：

把 ConfigurationClass 转换为 SourceClass，SourceClass 主要用于解析类的元信息（注解、方法等）。
这个转换是为了兼容不同的 BeanDefinition 类型，比如 XML 配置的 bean、注解 bean 等。
doProcessConfigurationClass(configClass, sourceClass, filter)：

解析 @ComponentScan、@Import、@ImportResource 等注解。
处理 @Bean 方法，注册 BeanDefinition。
处理 @PropertySource 读取外部属性文件。
处理 @EnableXXX 等启用特性。
while (sourceClass != null)：

这里是一个循环处理，确保所有的父类层级都能被解析。
例如，如果 configClass 继承了一个带有 @Configuration 的父类，Spring 也会解析它的父类。
4. 解析完成后，将类存入 configurationClasses
this.configurationClasses.put(configClass, configClass);
解析完成后，configClass 会存入 this.configurationClasses，避免重复解析。
执行流程
判断 @Conditional 是否满足条件，如果不满足，则跳过该类。
检查该类是否已经被解析：
如果是 @Import 进来的，则可能合并或忽略。
如果是显式注册的 Bean，则可能移除旧的 @Import 版本。
递归解析：
处理 @ComponentScan 扫描组件。
处理 @Bean 方法，注册 Bean。
处理 @Import 导入的其他配置类。
处理 @PropertySource 读取的外部配置文件。
处理 @EnableXXX 启用的特性（如 @EnableScheduling）。
解析完成后，存入 configurationClasses，避免重复解析。
关键概念
@Import 合并逻辑

如果类是 @Import 进来的，而已有的配置类不是 @Import 进来的，则忽略新的 @Import 版本，保留已有的。
如果两个都是 @Import 进来的，则合并 importedBy 信息。
递归解析配置类

Spring 不仅会解析当前 @Configuration 类，还会递归解析其父类。
解析过程中会注册 Bean

在 doProcessConfigurationClass 中，Spring 会找到 @Bean 方法，并注册 BeanDefinition。
总结
processConfigurationClass 主要用于 解析 @Configuration 类，并注册 @Bean、处理 @Import 等。
它会 递归解析 该类及其父类，确保所有相关的配置都能被加载。
如果一个类是 @Import 进来的，而已有相同的类存在，Spring 可能会 合并或忽略 该类。
解析完成后，会 存入 configurationClasses，防止重复解析。
这个方法是 Spring @Configuration 处理的核心部分，决定了哪些配置类生效，哪些 @Bean 方法会被注册。
*/
protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
    // 1. 判断该配置类是否应该跳过解析
    if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
        return;
    }

    // 2. 检查该配置类是否已存在
    ConfigurationClass existingClass = this.configurationClasses.get(configClass);
    if (existingClass != null) {
        if (configClass.isImported()) {
            if (existingClass.isImported()) {
                // 如果该类是通过 @Import 导入的，并且之前已经解析过 @Import 的相同类，则合并导入信息
                existingClass.mergeImportedBy(configClass);
            }
            // 否则，忽略新导入的配置类，保留已有的非 @Import 配置的类
            return;
        } else {
            // 处理显式定义的配置类（即在 BeanDefinition 中直接注册的，而不是 @Import 进来的）
            // 可能是一个新的 bean 定义覆盖了之前的 @Import 定义，需要移除旧的配置类
            this.configurationClasses.remove(configClass);
            this.knownSuperclasses.values().removeIf(configClass::equals);
        }
    }

    // 3. 递归处理当前配置类及其父类
    SourceClass sourceClass = asSourceClass(configClass, filter);
    do {
        sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
    } while (sourceClass != null);

    // 4. 解析完成后，将该配置类存入已解析的配置类集合
    this.configurationClasses.put(configClass, configClass);
}
```

```java
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {
        // 1. 解析 @Component，处理内部类,如果该类使用了 @Component，则先递归处理它的 内部类（嵌套类）
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			 // 递归处理成员（嵌套）类,processMemberClasses 方法会找到 configClass 内部的静态类，并继续解析它们
			processMemberClasses(configClass, sourceClass, filter);
		}

		// 2. 解析 @PropertySource
         //@PropertySource 允许在 Spring 配置类中引入 外部属性文件，如 .properties 或 .yml 文件。
		//processPropertySource(propertySource) 方法会将 @PropertySource 注解的内容 添加到环境变量中（Environment）。
		//如果 Environment 不是 ConfigurableEnvironment，则忽略 @PropertySource。
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// 3. 解析 @ComponentScan，扫描组件
        // 处理 @ComponentScan，扫描 Spring 组件（@Component、@Service、@Repository、@Controller）。
        // this.componentScanParser.parse(...) 方法：
       		 // 解析 @ComponentScan 注解，并返回扫描到的 Bean 定义集合。
       		 // 遍历扫描到的 Bean，如果发现新的 @Configuration 类，则递归解析。
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
                 // 立即执行组件扫描
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
                  // 检查扫描到的 Bean 定义，并递归解析其中的配置类
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		//4. 解析 @Import
         // 普通 Java 配置类（@Configuration）
         // ImportSelector（选择性地返回要导入的类）
         // ImportBeanDefinitionRegistrar（手动注册 Bean）
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// 5. 解析 @ImportResource
         // 处理 @ImportResource，引入 XML 配置文件（例如 applicationContext.xml）。
		// configClass.addImportedResource(...) 将 XML 资源添加到 ConfigurationClass 进行后续解析。
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// 6. 解析 @Bean 方法
         // retrieveBeanMethodMetadata(sourceClass) 获取所有被 @Bean 标记的方法。
		// configClass.addBeanMethod(...) 将这些方法添加到 ConfigurationClass，并在后续阶段注册为 Bean。
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		//7. 处理接口中的 default 方法
         // 解析 @Configuration 类实现的接口中的 default 方法。
         // 这些 default 方法也可能包含 @Bean 定义，因此需要处理。
		processInterfaces(configClass, sourceClass);

		// 8. 解析 @Configuration 类的父类
         // 如果当前 @Configuration 类有父类，并且该父类不是 java.* 开头的（即不是 JDK 类），则递归解析。
         // 确保 @Configuration 的父类也能被解析，即 Spring 允许配置类继承另一个配置类。
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
                 // 返回父类的 SourceClass，递归解析
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete 
         // 无父类，则解析完成
		return null;
	}
```



##### 2.8.6.4 DeferredImportSelectorHandler

```java
    /**
     * process() 方法核心功能
     * 获取并清空 deferredImportSelectors。
     * 创建 DeferredImportSelectorGroupingHandler 进行分组管理。
     * 对 DeferredImportSelector 进行排序，保证顺序执行。
     * 注册所有 DeferredImportSelector 到 handler。
     * 调用 processGroupImports()，执行 selectImports() 逻辑，导入配置类。
     * 重新初始化 deferredImportSelectors，防止影响后续逻辑。
     * 作用
     * 这是 @Import(ImportSelector.class) 机制的一部分，用于处理 DeferredImportSelector 延迟导入。
     * DeferredImportSelector 允许开发者 按需加载 Spring 配置，并按特定规则选择要导入的类。
     * Spring Boot 自动配置（AutoConfiguration）依赖于此机制 来延迟导入配置。
     */
 public void process() {
    // 获取当前存储的所有延迟导入选择器（DeferredImportSelectorHolder）
    List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
    // 置空当前的 deferredImportSelectors，避免重复使用
    this.deferredImportSelectors = null;
    
    try {
        if (deferredImports != null) {
            // 创建一个处理器，用于管理和处理 DeferredImportSelector 组
            DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
            
            // 对 DeferredImportSelector 进行排序，确保按照优先级顺序执行
            deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
            
            // 遍历所有的延迟导入选择器，并将其注册到 handler 中
            deferredImports.forEach(handler::register);
            
            // 处理所有分组的导入操作，执行 selectImports() 逻辑
            handler.processGroupImports();
        }
    }
    finally {
        // 重新初始化 deferredImportSelectors，防止后续逻辑出错
        this.deferredImportSelectors = new ArrayList<>();
    }
}
    
```

```java
    /**
     * 遍历 groupings（DeferredImportSelectorGrouping 组）
     * groupings 存储的是不同 DeferredImportSelector 选择的类的分组，每个分组对应一个 DeferredImportSelectorGrouping。
     * 获取 exclusionFilter
     * 该过滤器用于排除某些不需要被导入的类，以防止重复或冲突。
     * 遍历 grouping.getImports()
     * getImports() 返回的是 DeferredImportSelectorGrouping.Entry 集合，每个 Entry 代表一个被 @Import 导入的类。
     * 调用 processImports 进行处理
     * configurationClass：当前 @Configuration 配置类。
     * asSourceClass(configurationClass, exclusionFilter)：转换 configurationClass 为 SourceClass 以便进一步解析。
     * Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter))：获取 @Import 导入的类并转换为 SourceClass 进行解析。
     * false 表示不进行递归处理（即不在 processImports 方法内再次处理 @Import）。
     */
public void processGroupImports() {
    // 遍历所有的 DeferredImportSelectorGrouping 组
    for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
        // 获取当前分组的排除过滤器（用于排除某些类）
        Predicate<String> exclusionFilter = grouping.getCandidateFilter();

        // 遍历当前分组的所有导入条目
        grouping.getImports().forEach(entry -> {
            // 获取当前导入类所属的配置类
            ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());

            try {
                // 处理 @Import 注解的类
                processImports(
                    configurationClass, 
                    asSourceClass(configurationClass, exclusionFilter), // 获取当前配置类的 SourceClass
                    Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)), // 获取要导入的类
                    exclusionFilter, 
                    false // 这里的 false 表示该方法不处理 @Import 注解的递归解析
                );
            }
            catch (BeanDefinitionStoreException ex) {
                // 如果出现 BeanDefinition 相关异常，则直接抛出
                throw ex;
            }
            catch (Throwable ex) {
                // 其他异常进行封装后抛出，便于调试
                throw new BeanDefinitionStoreException(
                        "Failed to process import candidates for configuration class [" +
                                configurationClass.getMetadata().getClassName() + "]", ex);
            }
        });
    }
}

```

```java
public Iterable<Group.Entry> getImports() {
    // 遍历所有延迟导入的 `DeferredImportSelectorHolder`
    for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
        // 处理每个 `DeferredImportSelectorHolder`
        // 这里 `process()` 方法的作用是让 `group` 处理配置类的元数据和 `ImportSelector`
        this.group.process(deferredImport.getConfigurationClass().getMetadata(),
                deferredImport.getImportSelector());
    }
    // 调用 `selectImports()` 方法，返回所有选中的 `ImportSelector` 导入的类
    return this.group.selectImports();
}

```

```java
    /**
     * processImports() 方法的作用是解析 @Import 注解导入的类，并递归处理导入的配置类，具体逻辑如下：
     * 检查导入类是否为空
     * 如果 importCandidates 为空，直接返回，避免不必要的处理。
     * 检测循环导入
     * 只有 checkForCircularImports == true 时，才会检查 importStack，避免死循环。
     * 如果发现循环导入问题，则使用 problemReporter 记录错误，并终止解析。
     * 遍历导入的候选类
     * 如果是 ImportSelector
     * 通过 selectImports() 获取需要导入的类，并递归调用 processImports() 继续解析。
     * 如果是 DeferredImportSelector，则交给 deferredImportSelectorHandler 处理。
     * 如果是 ImportBeanDefinitionRegistrar
     * 通过 addImportBeanDefinitionRegistrar() 注册额外的 Bean。
     * 普通的 @Configuration 类
     * 直接递归解析。
     */
private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
                            Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
                            boolean checkForCircularImports) {

    // 如果没有需要导入的类，直接返回
    if (importCandidates.isEmpty()) {
        return;
    }

    // 检查是否发生循环导入（当 checkForCircularImports 为 true 时）
    if (checkForCircularImports && isChainedImportOnStack(configClass)) {
        // 发现循环导入，报告错误
        this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
    } else {
        // 记录当前配置类到导入栈中，防止循环导入
        this.importStack.push(configClass);
        try {
            // 遍历所有需要导入的候选类
            for (SourceClass candidate : importCandidates) {

                if (candidate.isAssignable(ImportSelector.class)) {
                    // 候选类实现了 ImportSelector -> 让它来决定要导入的类
                    Class<?> candidateClass = candidate.loadClass();
                    ImportSelector selector = ParserStrategyUtils.instantiateClass(
                            candidateClass, ImportSelector.class, this.environment, this.resourceLoader, this.registry);

                    // 获取 ImportSelector 提供的排除过滤器
                    Predicate<String> selectorFilter = selector.getExclusionFilter();
                    if (selectorFilter != null) {
                        exclusionFilter = exclusionFilter.or(selectorFilter);
                    }

                    if (selector instanceof DeferredImportSelector) {
                        // 处理延迟导入的 ImportSelector
                        this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
                    } else {
                        // 立即获取需要导入的类
                        String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
                        Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
                        // 递归处理导入的类
                        processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
                    }
                } 
                else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                    // 候选类实现了 ImportBeanDefinitionRegistrar -> 注册额外的 Bean 定义
                    Class<?> candidateClass = candidate.loadClass();
                    ImportBeanDefinitionRegistrar registrar = ParserStrategyUtils.instantiateClass(
                            candidateClass, ImportBeanDefinitionRegistrar.class, this.environment,
                            this.resourceLoader, this.registry);

                    // 将 ImportBeanDefinitionRegistrar 添加到当前配置类
                    configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                } 
                else {
                    // 既不是 ImportSelector 也不是 ImportBeanDefinitionRegistrar
                    // 直接作为 @Configuration 配置类进行解析
                    this.importStack.registerImport(
                            currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());

                    // 递归解析导入的配置类
                    processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
                }
            }
        } 
        catch (BeanDefinitionStoreException ex) {
            throw ex;
        } 
        catch (Throwable ex) {
            throw new BeanDefinitionStoreException(
                    "Failed to process import candidates for configuration class [" +
                            configClass.getMetadata().getClassName() + "]", ex);
        } 
        finally {
            // 解析完成后，从导入栈中移除当前类
            this.importStack.pop();
        }
    }
}

```



##### 2.8.6.5 AutoConfigurationImportSelector

```java
public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
    // 断言检查，确保 deferredImportSelector 是 AutoConfigurationImportSelector 的实例
    Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
            () -> String.format("Only %s implementations are supported, got %s",
                    AutoConfigurationImportSelector.class.getSimpleName(),
                    deferredImportSelector.getClass().getName()));
    
    // 强制类型转换，将 deferredImportSelector 转换为 AutoConfigurationImportSelector
    // 获取与给定的 annotationMetadata 相关的 AutoConfigurationEntry 条目
    AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
        .getAutoConfigurationEntry(annotationMetadata);

    // 将获取到的 AutoConfigurationEntry 添加到 autoConfigurationEntries 列表中
    // autoConfigurationEntries 用于记录当前处理的自动配置条目
    this.autoConfigurationEntries.add(autoConfigurationEntry);

    // 遍历 AutoConfigurationEntry 中的配置类名称
    for (String importClassName : autoConfigurationEntry.getConfigurations()) {
        // 使用 putIfAbsent 方法，确保只有在 entries 中不存在相同的配置类时才会添加
        // entries 用于记录每个配置类的相关注解元数据
        this.entries.putIfAbsent(importClassName, annotationMetadata);
    }
}

```

```java
protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
    // 检查给定的注解元数据是否启用了自动配置
    if (!isEnabled(annotationMetadata)) {
        // 如果未启用自动配置，则返回一个空的 AutoConfigurationEntry
        return EMPTY_ENTRY;
    }
    
    // 获取与注解元数据相关的属性
    AnnotationAttributes attributes = getAttributes(annotationMetadata);
    
    // 根据注解元数据和属性获取候选的自动配置类列表
    List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
    
    // 移除候选配置类中的重复项
    configurations = removeDuplicates(configurations);
    
    // 获取排除的配置类列表
    Set<String> exclusions = getExclusions(annotationMetadata, attributes);
    
    // 检查是否有排除的类，并从候选配置类中移除它们
    checkExcludedClasses(configurations, exclusions);
    
    // 从配置类列表中移除排除的配置类
    configurations.removeAll(exclusions);
    
    // 使用配置类过滤器对配置类进行过滤
    configurations = getConfigurationClassFilter().filter(configurations);
    
    // 触发自动配置导入事件，传递配置类和排除项
    fireAutoConfigurationImportEvents(configurations, exclusions);
    
    // 返回包含最终配置类和排除项的 AutoConfigurationEntry
    return new AutoConfigurationEntry(configurations, exclusions);
}

```

```java
protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
    // 使用 SpringFactoriesLoader 加载工厂类名称，并将其存入 configurations 列表中
    List<String> configurations = new ArrayList<>(
            SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(), getBeanClassLoader()));

    // 从 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件中加载候选配置类
    ImportCandidates.load(AutoConfiguration.class, getBeanClassLoader()).forEach(configurations::add);

    // 如果 configurations 列表为空，则抛出异常，提示没有找到自动配置类
    Assert.notEmpty(configurations,
            "No auto configuration classes found in META-INF/spring.factories nor in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports. If you "
                    + "are using a custom packaging, make sure that file is correct.");

    // 返回所有候选配置类的列表
    return configurations;
}

```

#### 2.8.7 注册bean后置处理器

#### 2.8.8 初始化MessageSource

#### 2.8.9 初始化context的应用事件多播器

#### 2.8.10 初始化其他特殊的bean，留给子类实现

#### 2.8.11 初始化context的监听器

#### 2.8.12  完成bean工厂初始化（实例化->填充bean属性->执行Aware->初始化之前应用bean后置处理器->执行初始化方法->初始化之后应用bean后置处理器）

#### 2.8.13 完成刷新

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
