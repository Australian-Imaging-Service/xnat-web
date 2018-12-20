/*
 * web: PrearchiveDetails_files.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

XNAT.app.fileCounter={
 	load:function(){
		var catCallback={
            success:this.processCatalogs,
            failure:this.handleFailure,
            cache:false, // Turn off caching for IE
            scope:this
        };

		var tempURL=serverRoot+"/REST" + this.url +"/resources?format=json&sortBy=category,cat_id,label&timestamp=" + (new Date()).getTime();

        YAHOO.util.Connect.asyncRequest('GET',tempURL,catCallback,null,this);

		for(var sc=0;sc<this.scans.length;sc++){
			$("#scan"+this.scans[sc]+"Files").html("Loading...");
		}
	},
	processCatalogs:function(o){
    	var catalogs = JSON.parse(o.responseText).ResultSet.Result;
    	var prearchiveSessionFileCount = 0;
        var prearchiveSessionFileSize = 0.0;

    	for(var catC=0;catC<catalogs.length;catC++){

    		var scan=document.getElementById("scan"+catalogs[catC].cat_id+"Files");
    		if(scan!=null){
				if(catalogs[catC].file_count!=undefined && catalogs[catC].file_count!=null){
		  			scan.innerHTML=catalogs[catC].file_count + " files, "+ size_format(catalogs[catC].file_size);
		  			if(catalogs[catC].file_count>0) {
                        prearchiveSessionFileCount = prearchiveSessionFileCount + parseInt(catalogs[catC].file_count,10);
                    }
                    if(catalogs[catC].file_size>0) {
                        prearchiveSessionFileSize = prearchiveSessionFileSize + parseFloat(catalogs[catC].file_size);
                    }
				}else{
		  			scan.innerHTML=size_format(catalogs[catC].file_size);
				}
    		}
    	}

    	// use jQuery to select 'prearchiveSessionTotals' so it fails silently if the element is not present
        $("#prearchiveSessionTotals").html("<b>Total:</b> "+ prearchiveSessionFileCount +" Files, " + size_format(prearchiveSessionFileSize));

		for(var sc=0;sc<this.scans.length;sc++){
			var fileDiv=document.getElementById("scan"+this.scans[sc]+"Files");
			if(fileDiv!=null && fileDiv!=undefined && fileDiv.innerHTML.startsWith("Load")){
				fileDiv.innerHTML="0 files";
		}
   },
	handleFailure:function(o){
		for(var sc=0;sc<this.scans.length;sc++){
			$("#scan"+this.scans[sc]+"Files").html("-");
		}
	},
	scans:[]
 };


function number_format(number, decimals, dec_point, thousands_sep) {
    var n = number, c = isNaN(decimals = Math.abs(decimals)) ? 2 : decimals;
    var d = dec_point == undefined ? "," : dec_point;
    var t = thousands_sep == undefined ? "." : thousands_sep, s = n < 0 ? "-" : "";
    var i = parseInt(n = Math.abs(+n || 0).toFixed(c)) + "", j = (j = i.length) > 3 ? j % 3 : 0;
    return s + (j ? i.substr(0, j) + t : "") + i.substr(j).replace(/(\d{3})(?=\d)/g, "$1" + t) + (c ? d + Math.abs(n - i).toFixed(c).slice(2) : "");
}


function size_format(filesize) {
    if (filesize >= 1073741824) {
        filesize = number_format(filesize / 1073741824, 2, '.', '') + ' GB';
    }
    else {
        if (filesize >= 1048576) {
            filesize = number_format(filesize / 1048576, 2, '.', '') + ' MB';
        }
        else {
            if (filesize >= 1024) {
                filesize = number_format(filesize / 1024, 0) + ' KB';
            }
            else {
                filesize = number_format(filesize, 0) + ' bytes';
            }
        }
    }
    return filesize;
}
