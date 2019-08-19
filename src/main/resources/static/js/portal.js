$("#directUploadInput").on("click", uploadFiles);

function getTextFileBodyInS3() {
    var messagePanel = $("#message-panel");
    if(messagePanel != null){
        messagePanel.remove();
    }
    $.get("/getTextFileBody", function (data) {
        $("#result").append('<span id="message-panel">' + data.toString() + ' </span>');
    });
}

function downloadFile() {
    $.get("/downloadFile", function (data) {
        var downloadLink = document.createElement("a");
        downloadLink.href = data.toString();
        downloadLink.click();
    })
}

function uploadFiles(){
    var progressPanel = $("#progress");
    if(progressPanel != null){
        progressPanel.remove();
    }
    // ダイアログボタンを押すと表示する。
    $("#directUploadForm").after('<div id="progress"><span id="pre-message" class="errorMessage">アップロードの準備中…</span></div>');
    var formData = new FormData;
    // ダイアログを選択すると呼ばれる。
    $("#directUploadInput").fileupload({
        forceIframeTransport : true,
//        url: "" // url option is for hr, not working
        acceptFileTypes: /(\.|\/)(txt|gif|jpe?g|png|mp4|MOV|mov|m4v)$/i,
        formData: formData,
        // ダイアログでFileを選択すると呼ばれる。(マルチアップロードがあることを想定)
        add: function(e, data){
            // S3へダイレクトアップロードするURLおよび認証情報をサーバへ要求する。
            $.ajax({
                url: $("#directUploadInput").data("authorizationUrl"),
                type: "GET",
                async:true,
            }).done(function(directUploadAuthorization, textStatus, jqXHR){
                $("pre-message").remove();
                var uploadUrl;
                // ダイアログで選択したファイルごとに、進行状況を表示しながらアップロードする。
                $.each(data.originalFiles, function(index, value){
                    var fileName = data.files[index].name.replace(".", "-");
                    $("#progress-" + fileName).remove();
                    $("#errorMessage-" + fileName).remove();
                    $("#progress").appendChild(
                        $('<div id="progress-' + fileName +
                          '><div class="progress-bar"><span id="progress-ratio-' + fileName +
                          '">0</span>%</div>'));
                    $('#progress-' + fileName + ' .progress-bar').css('width', 0 + '%');
                    $("#progress-" + fileName).before($('<span id="upload-label-' + fileName
                        + '" class="successMessage">アップロード進行状況： '
                        + data.files[index].name + '<br></span>'));
                    // アップロードするファイルがサポート形式か判定する
                    var isSupportedFile = false;
                    switch (data.files[index].type) {
                        case "text/plain":
                            isSupportedFile = true;
                            break;
                        case "image/gif":
                            isSupportedFile = true;
                            break;
                        case "image/jpeg":
                            isSupportedFile = true;
                            break;
                        case "image/png":
                            isSupportedFile = true;
                            break;
                        case "video/mpeg":
                            isSupportedFile = true;
                            break;
                        case "video/mp4":
                            isSupportedFile = true;
                            break;
                        case "video/quicktime":
                            isSupportedFile = true;
                            break;
                        default:
                            break;
                    }
                    formData = new FormData();
                    if(isSupportedFile){
                        uploadUrl = directUploadAuthorization.uploadUrl;
                        formData.append('key', directUploadAuthorization.objectKey + data.files[index].name);
                        formData.append('x-amz-credential', directUploadAuthorization.credential);
                        formData.append('acl', directUploadAuthorization.acl);
                        formData.append('x-amz-security-token', directUploadAuthorization.securityToken);
                        formData.append('x-amz-algorithm', directUploadAuthorization.algorithm);
                        formData.append('x-amz-date', directUploadAuthorization.date);
                        formData.append('policy', directUploadAuthorization.policy);
                        formData.append('x-amz-signature', directUploadAuthorization.signature);
                        formData.append('file', data.files[index]);
                        // S3へダイレクトアップロードする
                        $.ajax({
                            url: uploadUrl,
                            type: 'POST',
                            dataType: 'json',
                            data: formData,
                            contentType: false,
                            processData: false,
                            xhr : function(){
                                var XHR = $.ajaxSettings.xhr();
                                if(XHR.upload){
                                    XHR.upload.addEventListener('progress',function(e){
                                        var progre = parseInt(e.loaded/e.total*100);
                                        $('#progress-' + fileName + ' .progress-bar').css({width: ((progre+1)*95/100)+'%'});
                                        $("#progress-ratio-" + fileName).text(progre);
                                    }, false);
                                }
                                return XHR;
                            },
                        }).done(function(data, textStatus, jqXHR){
                            $("#upload-label-" + fileName).html($("#upload-label-" + fileName).html() + " アップロードが完了しました。<br/>");
                            $("#progress-" + fileName).remove();
                        }).fail(function(jqXHR, textStatus, errorThrown){
                            $("#errorMessage-" + fileName).remove();
                            $("#thumbnailPanel").after($('<span id="errorMessage-' + fileName + '" class="errorMessage">'
                                + 'ファイルアップロード中にエラーが発生しました。' + fileName + '</span>'));
                        });
                    }else {
                        $("#errorMessage-" + fileName).remove();
                        $("#thumbnailPanel").before($('<span id="errorMessage-' + fileName
                            + '" class="errorMessage">サポートされない形式です。'
                            + fileName + '</span>'));
                    }
                });
            }).fail(function(jqXHR, textStatus, errorThrown){});
        }, done : function(e, data){}, fail: function(e, data){}
    });
}