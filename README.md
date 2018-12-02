# Spring Boot实现TUS协议实现文件断点上传


## 关于Tus

> TUS协议提供一种基于 HTTP/1.1 和 HTTP/2 机制用于文件断点续传。

## 举例

`HEAD`请求用来查询某个文件上传进度使用
下面例子中一个文件总共100个Byte，已经传输了70个Byte，所有交互内容都在HTTP请求头中。

**HEAD Request:**

```
HEAD /files/24e533e02ec3bc40c387f1a0e460e216 HTTP/1.1
Host: tus.example.org
Tus-Resumable: 1.0.0
```

**Response:**

```
HTTP/1.1 200 OK
Upload-Offset: 70
Tus-Resumable: 1.0.0
```

拿到`Upload-Offset`, 使用`PATCH`方式请求继续传输文件的未完成部分。


**PATCH Request:**

```
PATCH /files/24e533e02ec3bc40c387f1a0e460e216 HTTP/1.1
Host: tus.example.org
Content-Type: application/offset+octet-stream
Content-Length: 30
Upload-Offset: 70
Tus-Resumable: 1.0.0

[remaining 30 bytes]
```

**Response:**

```
HTTP/1.1 204 No Content
Tus-Resumable: 1.0.0
Upload-Offset: 100
```

以上就完成了传输。

## 请求类型


- OPTIONS请求

  > 主要是获取协议描述，支持的各种参数，协议细节，其实tus使用`Header`来进行服务器和客户端信息交互，`OPTIONS`需要实现两个Action，一个用于总的协议描述，另一个可以获取到当前文件的上传进度`Offset`。


- POST请求

  > 当有新文件需要上传时候，注册文件信息，文件名，文件大小，这些内容，tus-js-client的文件名是Base64格式的。

- HEAD请求

  > 请求当前文件的服务器信息，返回文件大小和当前进度。


- PATCH请求

  > 上传文件，写入磁盘系统。

- Get请求

  > 下载文件，根据guid

- DELETE请求

  > 删除文件



## 大致流程

流程见下图，不解释了

![tus](/Users/freewolf/Documents/Study/tus/tus.jpg)



## 客户端

客户端本文使用js-tus-client项目，这个项目本地自行启动，有node.js环境的，如下：

```
npm install -g http-server
cd html
http-server .
```

然后打开浏览器8080端口，就可以看到页面了。

Simple.html 是最简单的一个文件上传页面demo。

其他都是js-tus-client的内容。

tus需要本地浏览器中存储已经上传的文件信息，这些js-tus-client都已经实现。




## 参考文献

- 关于TUS协议 https://tus.io/protocols/resumable-upload.html

- 本文中用到的js client https://github.com/tus/tus-js-client
- 关于OPTIONS请求 https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS