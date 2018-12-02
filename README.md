# Spring Boot实现TUS协议实现文件断点上传


## 关于Tus

> > TUS协议提供一种基于 HTTP/1.1 和 HTTP/2 机制用于文件断点续传。

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


- POST请求

- HEAD请求


- PATCH请求










## 参考文献

关于OPTIONS请求
https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS

https://github.com/tus/tus-js-client

https://tus.io/protocols/resumable-upload.html