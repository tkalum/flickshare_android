//flick_rust/src/lib.rs
use jni::objects::{JClass, JString, JObject};
use jni::JNIEnv;
use jni::sys::{jint, jstring};
use std::fs::File;
use std::os::unix::io::FromRawFd; // Required to use FDs
use std::net::{TcpStream, TcpListener};
use std::io::{Read, Write, BufReader};

#[no_mangle]
pub extern "system" fn Java_com_example_flickshare_jni_RustBridge_getFileInfo(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    filename: JString,
) -> jstring {
    // Safety: We are taking ownership of a file handle provided by Android
    let file = unsafe { File::from_raw_fd(fd) };

    let file_name: String = env.get_string(&filename).expect("Couldn't get filename").into();

    let result_text = match file.metadata() {
        Ok(metadata) => format!("Success!\nFile Name: {}\nSize: {} bytes", file_name, metadata.len()),
        Err(e) => format!("Rust FD Error: {}", e),
    };

    let output = env.new_string(result_text).unwrap();
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_flickshare_jni_RustBridge_sendFile(
    mut env: JNIEnv,
    _class: JClass,
    port: jint,
    fd: jint,
    listener: JObject,
) -> jstring {
    let addr = format!("0.0.0.0:{}", port);
    
    // 1. Start Listener on Android
    let server = match TcpListener::bind(&addr) {
        Ok(l) => l,
        Err(e) => return env.new_string(format!("Bind Error: {}", e)).unwrap().into_raw(),
    };

    // 2. Wait for the PC to connect (Accept)
    let (mut stream, addr) = match server.accept() {
        Ok(res) => res,
        Err(e) => return env.new_string(format!("Accept Error: {}", e)).unwrap().into_raw(),
    };

    let remote_ip = addr.ip().to_string();
    
    let mut data_stream = match TcpStream::connect(format!("{}:24243", remote_ip)) {
        Ok(s) => s,
        Err(e) => return env.new_string(format!("Failed to connect back to PC: {}", e)).unwrap().into_raw(),
    };

    // 3. Send File Info first (Optional, but good for Go side to know what's coming)
    // For now, let's just stream the raw bytes
    let file = unsafe { File::from_raw_fd(fd) };
    let mut reader = BufReader::new(file);
    let mut buffer = [0; 65536];
    let mut total_sent: i64 = 0;

    // 4. Push data to PC
    loop {
        match reader.read(&mut buffer) {
            Ok(0) => break,
            Ok(n) => {
                if data_stream.write_all(&buffer[..n]).is_err() { break; }
                total_sent += n as i64;
                
                // Update Kotlin UI progress
                let _ = env.call_method(&listener, "onProgress", "(J)V", &[total_sent.into()]);
            }
            Err(_) => break,
        }
    }

    env.new_string("File Sent Successfully").unwrap().into_raw()
}



#[no_mangle]
pub extern "system" fn Java_com_example_flickshare_jni_RustBridge_receiveFile(
    mut env: JNIEnv,
    _class: JClass,
    remote_ip: JString,   // The IP of the device that has the file
    remote_port: jint,    // The Port of the device that has the file
    fd: jint,             // Local File Descriptor to save the data
    listener: JObject
) -> jstring {
    let remote_ip_str: String = env.get_string(&remote_ip).expect("Invalid IP").into();
    let remote_addr = format!("{}:{}", remote_ip_str, remote_port);

    // 1. OPEN LOCAL LISTENER
    // We bind to 0 so the OS picks a port, or you can stay with 24243
    let local_listener = match TcpListener::bind("0.0.0.0:24243") {
        Ok(l) => l,
        Err(e) => return env.new_string(format!("Local Bind Error: {}", e)).unwrap().into_raw(),
    };

    // 2. HANDSHAKE: Connect to Remote to trigger the transfer
    // This allows the Remote device to see our IP via the connection
    if let Ok(mut stream) = TcpStream::connect(&remote_addr) {
        let _ = stream.write_all(b"NOTIFY_RECEIVE_READY"); 
    } else {
        return env.new_string("Could not notify remote device").unwrap().into_raw();
    }

    // 3. WAIT FOR DATA CONNECTION
    // The Remote device will now Dial us back at 24243
    let (mut data_stream, _) = match local_listener.accept() {
        Ok(res) => res,
        Err(e) => return env.new_string(format!("Accept Error: {}", e)).unwrap().into_raw(),
    };

    let mut file = unsafe { File::from_raw_fd(fd) };
    let mut buffer = [0; 65536];
    let mut total_bytes: i64 = 0;
    let mut last_update_bytes: i64 = 0;

    // 4. STREAM DATA TO FILE
    loop {
        match data_stream.read(&mut buffer) {
            Ok(0) => break, 
            Ok(n) => {
                if file.write_all(&buffer[..n]).is_err() { break; }
                total_bytes += n as i64;
                
                if total_bytes - last_update_bytes > 524288 {
                    let _ = env.call_method(&listener, "onProgress", "(J)V", &[total_bytes.into()]);
                    last_update_bytes = total_bytes;
                }
            }
            Err(_) => break,
        }
    }

    env.new_string("Transfer Successful").unwrap().into_raw()
}



