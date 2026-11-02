import { ElMessage } from 'element-plus'

let messageInstance = null;

const message=function(type,msg,duration=4000){
    if(messageInstance !=null){
        messageInstance.close()
    }
    // 顶部导航栏高 60px，提示框从其下方出现，避免盖住菜单
    messageInstance = ElMessage({ type, message: msg, zIndex: 3000, duration, offset: 72 })
    return messageInstance
}


const error = function (msg) {
    return message('error',msg)
}

const success = function (msg) {
    return message('success',msg)
}

const info = function (msg) {
    return message('info',msg)
}

const warning = function (msg) {
    return message('warning',msg)
}
const loading = function(msg){
    return message('info',msg)
}

const mMessage = {
    error,
    success,
    info,
    warning,
    loading,
    message
}

export default mMessage;
