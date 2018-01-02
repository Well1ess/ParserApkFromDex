package nim.shs1330.netease.com.parserapkfromdex;


/**
 * 类加载机制是父类委托
 * 当两个插件有依赖关系的时候，可以使用源apk的ClassLoader来生成自己的DexClassLoader
 * {@link dalvik.system.DexClassLoader#
 * DexClassLoader(java.lang.String, java.lang.String, java.lang.String, java.lang.ClassLoader)}
 * 最后一个参数是父类加载器，当在插件中时，插件委托宿主，宿主委托。。。父类加载不了再委托子类，再次下放
 * 到插件中完成加载，这样引入的v4，v7插件包就可以使用了。
 *
 */

