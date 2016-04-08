<%@tag description="Default layout template" pageEncoding="UTF-8" %>
<%@attribute name="title" %>

<!DOCTYPE html>
<!--[if lt IE 7]> <html class="ie ie6 ltie7 ltie8 ltie9 ltie10 no-js"> <![endif]-->
<!--[if IE 7]> <html class="ie ie7 ltie8 ltie9 ltie10 no-js"> <![endif]-->
<!--[if IE 8]> <html class="ie ie8 ltie9 ltie10 no-js"> <![endif]-->
<!--[if IE 9]> <html class="ie ie9 ltie10 no-js"> <![endif]-->
<!--[if gt IE 9]><!-->
<html class="no-js"> <!--<![endif]-->

<head>
    <title>${title}</title>

    <!-- HeaderIncludes -->

    <!-- path: /xnat-templates/navigations/HeaderIncludes -->

    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="cache-control" content="max-age=0">
    <meta http-equiv="cache-control" content="no-cache">
    <meta http-equiv="expires" content="-1">
    <meta http-equiv="expires" content="Tue, 01 Jan 1980 1:00:00 GMT">


    <!-- load polyfills before ANY other JavaScript -->
    <script type="text/javascript" src="/scripts/polyfills.js"></script>

    <!-- set global vars that are used often -->
    <script type="text/javascript">

        var XNAT = {};
        var serverRoot = "";
        var csrfToken = "66ccfc57-b8d2-4e90-8b9e-f1ff0b43e06c";
        var showReason = typeof false != 'undefined' ? false : null;
        var requireReason = typeof false != 'undefined' ? false : null;

    </script>

    <!-- XNAT global functions (no dependencies) -->
    <script type="text/javascript" src="/scripts/globals.js"></script>

    <!-- required libraries -->
    <script>
        // loads minified versions by default
        // add ?debug=true to the query string
        // or #debug to the url hash to load
        // the non-minified versions
        writeScripts([
            scriptUrl('lib/loadjs/loadjs'),
            scriptUrl('lib/jquery/jquery|.min'),
            scriptUrl('lib/jquery/jquery-migrate-1.2.1|.min'),
        ]);
    </script>
    <script type="text/javascript">
        // use 'jq' to avoid _possible_ conflicts with Velocity
        var jq = jQuery;
    </script>

    <!-- jQuery plugins -->
    <link rel="stylesheet" type="text/css" href="/scripts/lib/jquery-plugins/chosen/chosen.min.css?v=1.7.0a1">
    <script type="text/javascript" src="/scripts/lib/jquery-plugins/chosen/chosen.jquery.min.js"></script>
    <script type="text/javascript" src="/scripts/lib/jquery-plugins/jquery.maskedinput.min.js"></script>
    <script type="text/javascript" src="/scripts/lib/jquery-plugins/jquery.spawn.js"></script>

    <!-- other libraries -->
    <script type="text/javascript" src="/scripts/lib/spawn/spawn.js"></script>

    <!-- XNAT utility functions -->
    <script type="text/javascript" src="/scripts/utils.js"></script>

    <script type="text/javascript">

        /*
         * XNAT global namespace object, which will not be overwriten if
         * already defined. Also define some other top level namespaces.
         */
        extend(XNAT, {
            /*
             * Parent namespace that templates can use to put their
             * own namespace
             */
            app: {
                displayNames: {
                    singular: {
                        project: "Project",
                        subject: "Subject",
                        imageSession: "Session",
                        mrSession: "MR Session"
                    },
                    plural: {
                        project: "Projects",
                        subject: "Subjects",
                        imageSession: "Sessions",
                        mrSession: "MR Sessions"
                    }
                },
                siteId: "XNAT"
            },
            images: {
                grnChk: "/images/checkmarkGreen.gif",
                redChk: "/images/checkmarkRed.gif"
            },
            data: {
                context: {
                    projectName: "",
                    projectID: "",
                    project: "",
                    xsiType: "",
                    subjectLabel: "",
                    subjectID: "",
                    label: "",
                    ID: ""
                },
                timestamp: jq.now() // timestamp for the page when it loads
            }
        });

        if (XNAT.data.context.projectName === "") {
            XNAT.data.context.projectName = "";
        }

        // 'page' object is same as 'context' - easier to remember?
        XNAT.data.page = XNAT.data.context;

        XNAT.app.showLeftBar = true;
        XNAT.app.showLeftBarProjects = true;
        XNAT.app.showLeftBarFavorites = true;
        XNAT.app.showLeftBarSearch = true;
        XNAT.app.showLeftBarBrowse = true;

        window.available_elements = [];


        window.available_elements.getByName = function (name) {
            for (var aeC = 0; aeC < this.length; aeC++) {
                if (this[aeC].element_name == name) {
                    return this[aeC];
                }
            }
            // return empty object if not found
            return {}
        };


        // quickly reference today's date
        XNAT.data.todaysDate = {};
        // if today was January 23, 2013...
        // m (1), mm (01), d (23), dd (23), yyyy (2013), ISO/iso (2013-01-23), US/us (01/23/2013)
        (function (dateObj) {
            dateObj.date = new Date();
            dateObj.gotMonth = dateObj.date.getMonth();
            dateObj.m = (dateObj.gotMonth + 1).toString();
            dateObj.mm = (dateObj.m.length === 1) ? '0' + dateObj.m : dateObj.m;
            dateObj.d = dateObj.date.getDate().toString();
            dateObj.dd = (dateObj.d.length === 1) ? '0' + dateObj.d : dateObj.d;
            dateObj.yyyy = dateObj.date.getFullYear().toString();
            dateObj.ISO = dateObj.iso = dateObj.yyyy + '-' + dateObj.mm + '-' + dateObj.dd;
            dateObj.US = dateObj.us = dateObj.mm + '/' + dateObj.dd + '/' + dateObj.yyyy;
        })(XNAT.data.todaysDate);

    </script>
    <script type="text/javascript">
        // initialize "Chosen" menus on DOM load
        // all <select class="chosen-menu"> elements
        // will be converted
        // putting this here to be at the top of
        // the jQuery DOM-ready queue
        jq(function () {
            chosenInit()
        });
    </script>
    <script type="text/javascript" src="/scripts/xdat.js"></script>
    <script type="text/javascript" src="/scripts/DynamicJSLoad.js"></script>

    <!-- YAHOO USER INTERFACE files below here -->
    <script type="text/javascript" src="/scripts/yui/build/yahoo-dom-event/yahoo-dom-event.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/event/event-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/container/container-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/menu/menu-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/element/element-beta-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/button/button-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/connection/connection-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/treeview/treeview-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/cookie/cookie-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/tabview/tabview-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/datasource/datasource-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/resize/resize-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/dragdrop/dragdrop-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/datatable/datatable-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/paginator/paginator-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/build/json/json-min.js"></script>
    <script type="text/javascript" src="/scripts/yui/xnat_loader.js"></script>
    <script type="text/javascript" src="/scripts/LeftBarTreeView.js"></script>
    <script type="text/javascript" src="/scripts/justification/justification.js"></script>
    <script type="text/javascript">

        // The YUIDOM alias is used throughout XNAT
        var YUIDOM = YAHOO.util.Dom;

        XNAT.dom = getObject(XNAT.dom || {});
        XNAT.dom.addFormCSRF = function (__form) {
            __form = isDefined(__form) ? $$(__form) : jq('form');
            __form.append('<input type="hidden" name="XNAT_CSRF" value="' + csrfToken + '">')
        };

        jq(function () {
            // add hidden input with CSRF data
            // to all forms on page load
            XNAT.dom.addFormCSRF();
        });

    </script>

    <!-- YUI css -->
    <link rel="stylesheet" type="text/css" href="/scripts/yui/build/assets/skins/sam/skin.css?v=1.7.0a1">

    <!-- xdat.css and xnat.css loaded last to override YUI styles -->
    <link rel="stylesheet" type="text/css" href="/style/app.css?v=1.7.0a1">

    <link rel="stylesheet" type="text/css" href="/scripts/xmodal-v1/xmodal.css?v=1.7.0a1">
    <script type="text/javascript" src="/scripts/xmodal-v1/xmodal.js"></script>
    <script type="text/javascript" src="/scripts/xmodal-v1/xmodal-migrate.js"></script>

    <link rel="stylesheet" type="text/css" href="/scripts/tabWrangler/tabWrangler.css?v=1.7.0a1">
    <script type="text/javascript" src="/scripts/tabWrangler/tabWrangler.js"></script>

    <!-- date input stuff -->
    <link type="text/css" rel="stylesheet" href="/scripts/yui/build/calendar/assets/skins/sam/calendar.css?v=1.7.0a1">
    <script type="text/javascript" src="/scripts/yui/build/calendar/calendar-min.js"></script>
    <script type="text/javascript" src="/scripts/ezCalendar.js"></script>

    <!-- XNAT JLAPI scripts -->
    <script type="text/javascript" src="/scripts/xnat/url.js"></script>
    <script type="text/javascript" src="/scripts/xnat/xhr.js"></script>
    <script type="text/javascript" src="/scripts/xnat/ui/popup.js"></script>
    <script type="text/javascript" src="/scripts/xnat/ui/dialog.js"></script>

    <!-- /HeaderIncludes -->

    <!-- path: xnat-templates/navigations/bodyOpen -->
</head>
<body id="page_body" class="yui-skin-sam">
<div id="page_wrapper">

    <!-- path: xdat-templates/layouts/Default -->

    <!-- START: xnat-templates/navigations/DefaultTop.vm -->
    <script type="text/javascript" src="/scripts/lib/js.cookie.js"></script>

    <style type="text/css">
        #attention_icon {
            float: left;
            padding-top: 7px;
            padding-left: 11px;
        }

        #attention_icon:hover {
            cursor: pointer;
        }
    </style>

    <div id="user_bar">
        <div class="inner">
            <img id="attention_icon" src="/images/attention.png" style="display:none;"
                 alt="attention needed - click for more info" title="attention needed - click for more info">
                                    <span id="user_info">Logged in as: &nbsp;<a
                                            href="/app/template/XDATScreen_UpdateUser.vm">admin</a> <b>|</b><span
                                            class="tip_icon" style="margin-right:3px;left:2px;top:3px;">
            <span class="tip shadowed"
                  style="top:20px;z-index:10000;white-space:normal;left:-150px;width:300px;background-color:#ffc;">Your XNAT session will auto-logout after a certain period of inactivity. You can reset that timer without reloading the page by clicking "renew."</span>
        </span>
                        Auto-logout in: <b id="timeLeft">-:--:--</b> - <a id="timeLeftRenew" href="javascript:"
                                                                          onClick="XNAT.app.timeout.handleOk()">renew</a> <b>|</b> <a
                                                id="logout_user" href="/app/action/LogoutUser">Logout</a></span>
            <script type="text/javascript">
                Cookies.set('guest', 'false', {path: '/'});
            </script>
            <div class="clear"></div>
        </div>
    </div><!-- /user_bar -->


    <div id="main_nav">

        <ul class="nav">
            <!-- Sequence: 10 -->
            <!-- allowGuest: true -->
            <li>
                <a id="nav-home" title="Home" href="/app/template/Index.vm">&nbsp;</a>
                <script>
                    $('#nav-home').css({
                        width: '30px',
                        backgroundImage: "url('/images/xnat-nav-logo-white-lg.png')",
                        backgroundRepeat: 'no-repeat',
                        backgroundSize: '32px',
                        backgroundPosition: 'center'
                    });
                </script>
            </li>
            <!-- Sequence: 20 -->

            <li><a href="#new">New</a>
                <ul>
                    <!-- Sequence: 10 -->
                    <li><a href="/app/template/XDATScreen_add_xnat_projectData.vm">Project</a></li>
                    <!-- -->
                    <!-- -->
                    <li>
                        <a href="/app/action/XDATActionRouter/xdataction/edit/search_element/xnat%3AsubjectData">Subject</a>
                    </li>
                    <!-- -->

                    <!-- no object found -->
                    <!-- no object found -->
                    <!-- no object found -->
                    <!-- -->
                    <li><a href="/app/template/XDATScreen_add_experiment.vm">Experiment</a></li>
                    <!-- -->
                    <!-- -->
                </ul>
            </li>
            <!-- Sequence: 30 -->
            <li><a href="#upload">Upload</a>
                <ul>
                    <!-- Sequence: 10 -->
                    <!-- Upload/Default -->
                    <li><a href="/app/template/LaunchUploadApplet.vm">Images</a></li>
                    <li><a href="/app/template/XMLUpload.vm">XML</a></li>
                    <li><a href="/app/template/XDATScreen_uploadCSV.vm">Spreadsheet</a></li>
                    <li><a href="/app/template/XDATScreen_prearchives.vm">Go to prearchive</a></li>
                </ul>
            </li>
            <!-- Sequence: 40 -->

            <li><a href="#adminbox">Administer</a>
                <ul>
                    <!-- Sequence: 10 -->
                    <li><a href="/app/template/XDATScreen_admin.vm">Users</a></li>
                    <li><a href="/app/template/XDATScreen_groups.vm">Groups</a></li>
                    <li><a href="/app/template/XDATScreen_dataTypes.vm">Data Types</a></li>
                    <li><a href="/app/template/XDATScreen_email.vm">Email</a></li>
                    <li><a href="/app/template/XDATScreen_manage_pipeline.vm">Pipelines</a></li>
                    <li><a href="/app/template/Configuration.vm">Configuration</a></li>
                    <li><a href="/app/template/Scripts.vm">Automation</a></li>
                    <li><a href="/app/template/XDATScreen_admin_options.vm">More...</a></li>
                </ul>
            </li>

            <!-- Title: Tools -->
            <!-- Sequence: 50 -->
            <!-- allowGuest: true -->

            <li><a href="#tools">Tools</a>
                <ul>
                    <!-- Sequence: 10 -->
                    <!-- allowGuest: true -->
                    <li><a href="https://wiki.xnat.org/display/XNAT16/XNAT+Desktop" target="_blank">XNAT Desktop
                        (XND)</a></li>
                    <li><a href="http://nrg.wustl.edu/projects/DICOM/DicomBrowser.jsp" target="_blank">DICOM Browser</a>
                    </li>
                    <li><a href="https://wiki.xnat.org/display/XNAT16/XNAT+Client+Tools" target="_blank">Command Prompt
                        Tools</a></li>
                </ul>
            </li>
            <!-- Sequence: 60 -->
            <li><a href="#help">Help</a>
                <ul>
                    <!-- Sequence: 10 -->
                    <!-- Home/Default -->
                    <li><a href="/app/template/ReportIssue.vm">Report a Problem</a></li>
                    <li><a href="http://wiki.xnat.org/display/XNAT16/Home" target="_blank">Documentation</a></li>
                </ul>
            </li>
        </ul>

        <!-- search script -->
        <script type="text/javascript">
            <!--
            function DefaultEnterKey(e, button) {
                var keynum, keychar, numcheck;

                if (window.event) // IE
                {
                    keynum = e.keyCode;
                    if (keynum == 13) {
                        submitQuickSearch();
                        return true;
                    }
                }
                else if (e) // Netscape/Firefox/Opera
                {
                    keynum = e.which;
                    if (keynum == 13) {
                        submitQuickSearch();
                        return false;
                    }
                }
                return true;
            }

            function submitQuickSearch() {
                concealContent();
                if (document.getElementById('quickSearchForm').value != "")
                    document.getElementById('quickSearchForm').submit();
            }

            //-->
        </script>
        <!-- end search script -->

        <style type="text/css">
            #quickSearchForm .chosen-results {
                max-height: 500px;
            }

            #quickSearchForm .chosen-results li {
                padding-right: 20px;
                white-space: nowrap;
            }

            #quickSearchForm .chosen-container .chosen-drop {
                width: auto;
                min-width: 180px;
                max-width: 360px;
            }

            #quickSearchForm .chosen-container .chosen-drop .divider {
                padding: 0;
                overflow: hidden;
            }
        </style>

        <form id="quickSearchForm" method="post" action="/app/action/QuickSearchAction">
            <select id="stored-searches" data-placeholder="Stored Searches">
                <option></option>
                <optgroup>
                    <option value="/app/template/XDATScreen_search_wizard1.vm">Advanced Search&hellip;</option>
                </optgroup>
                <optgroup class="stored-search-list">
                    <option disabled>(no stored searches)</option>
                    <!-- stored searches will show up here -->
                </optgroup>
            </select>
            <input id="searchValue" class="clean" name="searchValue" type="text" maxlength="40" size="20" value=""/>
            <button type="button" id="search_btn" class="btn2" onclick="submitQuickSearch();">Go</button>

            <script>
                $('#searchValue').each(function () {
                    this.value = this.value || 'search';
                    $(this).focus(function () {
                        $(this).removeClass('clean');
                        if (!this.value || this.value === 'search') {
                            this.value = '';
                        }
                    })
                });
                $('#stored-searches').on('change', function () {
                    if (this.value) {
                        window.location.href = this.value;
                    }
                }).chosen({
                    width: '150px',
                    disable_search_threshold: 9,
                    inherit_select_classes: true,
                    placeholder_text_single: 'Stored Searches',
                    search_contains: true
                });
            </script>
        </form>


    </div>
    <!-- /main_nav -->

    <!-- main_nav interactions -->
    <script type="text/javascript">

        (function () {

            // cache it
            var main_nav$ = jq('#main_nav > ul');

            var body$ = jq('body');

            var cover_up_count = 1;

            function coverApplet(el$) {
                var cover_up_id = 'cover_up' + cover_up_count++;
                var jqObjPos = el$.offset(),
                        jqObjLeft = jqObjPos.left,
                        jqObjTop = jqObjPos.top,
                        jqObjMarginTop = el$.css('margin-top'),
                        jqObjWidth = el$.outerWidth() + 4,
                        jqObjHeight = el$.outerHeight() + 2;

                el$.before('<iframe id="' + cover_up_id + '" class="applet_cover_up" src="about:blank" width="' + jqObjWidth + '" height="' + jqObjHeight + '"></iframe>');

                jq('#' + cover_up_id).css({
                    display: 'block',
                    position: 'fixed',
                    width: jqObjWidth,
                    height: jqObjHeight,
                    marginTop: jqObjMarginTop,
                    left: jqObjLeft,
                    top: jqObjTop,
                    background: 'transparent',
                    border: 'none',
                    outline: 'none'
                });
            }

            function unCoverApplets(el$) {
                el$.prev('iframe.applet_cover_up').detach();
            }

            function fadeInNav(el$) {
                el$.find('> ul').show().addClass('open');
            }

            function fadeOutNav(el$) {
                el$.find('> ul').hide().removeClass('open');
            }

            // give menus with submenus a class of 'more'
            main_nav$.find('li ul, li li ul').closest('li').addClass('more');
            main_nav$.find('li li ul').addClass('subnav');

            // no fancy fades on hover
            main_nav$.find('li.more').on('mouseover',
                    function () {
                        var li$ = $(this);
                        fadeInNav(li$);
                        li$.find('ul.subnav').each(function () {
                            var sub$ = $(this);
                            var offsetL = sub$.closest('ul').outerWidth();
                            sub$.css({'left': offsetL + -25})
                        });
                        if (body$.hasClass('applet')) {
                            coverApplet(li$.find('> ul'));
                        }
                    }
            ).on('mouseout',
                    function () {
                        var li$ = $(this);
                        fadeOutNav(li$);
                        if (body$.hasClass('applet')) {
                            unCoverApplets(li$.find('> ul'));
                        }
                    }
            );

            // clicking the "Logout" link sets the warning bar cookie to 'OPEN' so it's available if needed on next login
            jq('#logout_user').click(function () {
                Cookies.set('WARNING_BAR', 'OPEN', {path: '/'});
                Cookies.set('NOTIFICATION_MESSAGE', 'OPEN', {path: '/'});
            });

        })();
    </script>
    <!-- end main_nav interactions -->

    <div id="header" class="main_header">
        <div class="pad">

            <a id="header_logo" href="/app/template/Index.vm" style="display:none;">
                <img class="logo_img" src="/images/logo.png" style="border:none;"> </a>

        </div>
    </div>  <!-- /header -->


    <script type="text/javascript">

        (function () {

            var header_logo$ = $('#header_logo');

            // adjust height of header if logo is taller than 65px
            var hdr_logo_height = header_logo$.height();
            if (hdr_logo_height > 65) {
                jq('.main_header').height(hdr_logo_height + 10);
            }

            // adjust width of main nav if logo is wider than 175px
            var hdr_logo_width = header_logo$.width();
            if (hdr_logo_width > 175) {
                jq('#main_nav').width(932 - hdr_logo_width - 20);
            }

            //
            //var recent_proj_height = jq('#min_projects_list > div').height();
            var recent_proj_height = 67;
            //jq('#min_projects_list, #min_expt_list').height(recent_proj_height * 5).css({'min-width':349,'overflow-y':'scroll'});

        })();

        logged_in = true;

        // initialize the advanced search method toggler
        XNAT.app.searchMethodToggler = function (_parent) {

            _parent = $$(_parent || 'body');

            var INPUTS = 'input, select, textarea, :input',
                    SEARCH_METHOD_CKBOXES = 'input.search-method',
                    __searchGroups = _parent.find('div.search-group'),
                    __searchMethodInputs = _parent.find(SEARCH_METHOD_CKBOXES);

            // disable 'by-id' search groups by default
            __searchGroups.filter('.by-id').addClass('disabled').find(INPUTS).not(SEARCH_METHOD_CKBOXES).changeVal('').prop('disabled', true).addClass('disabled');

            // enable 'by-criteria' search groups by default
            __searchGroups.filter('.by-criteria').removeClass('disabled').find(INPUTS).prop('disabled', false).removeClass('disabled');

            // check 'by-criteria' checkboxes
            __searchMethodInputs.filter('.by-criteria').prop('checked', true);

            // don't add multiple click handlers
            __searchMethodInputs.off('click');

            // toggle the search groups
            __searchMethodInputs.on('click', function () {

                var method = this.value,
                        isChecked = this.checked;

                __searchGroups.addClass('disabled').find(INPUTS).not(SEARCH_METHOD_CKBOXES).changeVal('').prop('disabled', true).addClass('disabled');

                __searchGroups.filter('.' + method).removeClass('disabled').find(INPUTS).prop('disabled', false).removeClass('disabled');

                // update the radio buttons/checkboxes
                __searchMethodInputs.prop('checked', false);
                __searchMethodInputs.filter('.' + method).prop('checked', true);
                chosenUpdate();
            });
        };

    </script>

    <script src="/scripts/timeLeft.js"></script>

    <div id="tp_fm"></div>

    <!-- END: xnat-templates/navigations/DefaultTop.vm -->

    <!-- /xnat-templates/navigations/DefaultLeft.vm -->

    <div id="l_tv" style="display:none;"></div>

    <script type="text/javascript">
        //build element_array
        var lTV = new LeftBarTreeView({treeview: "l_tv"});

        lTV.loadNodeData = function (node, fnLoadComplete) {
            var tabReqObject = node.data;
            tabReqObject.label = node.label;
            tabReqObject.node = node;
            if (tabReqObject.URL != undefined) {
                window.location = serverRoot + "/app/template/Search.vm/node/" + tabReqObject.ID;
            }
            else {
                if (tabReqObject.ID != undefined && tabReqObject.ID == "ss") {
                    var callback = {
                        cache: false, // Turn off caching for IE
                        success: function (oResponse) {
                            var oResults = eval("(" + oResponse.responseText + ")");
                            if ((oResults.ResultSet.Result) && (oResults.ResultSet.Result.length)) {
                                for (var ssC = 0; ssC < oResults.ResultSet.Result.length; ssC++) {
                                    var cpNode = new YAHOO.widget.TextNode({
                                        label: oResults.ResultSet.Result[ssC].brief_description,
                                        ID: "ss." + oResults.ResultSet.Result[ssC].id,
                                        SS_ID: oResults.ResultSet.Result[ssC].id,
                                        URL: serverRoot + '/REST/search/saved/' + oResults.ResultSet.Result[ssC].id + '',
                                        TITLE: oResults.ResultSet.Result[ssC].description
                                    }, oResponse.argument.node, false);
                                }
                            }
                            oResponse.argument.fnLoadComplete();
                        },
                        failure: function (oResponse) {
                            oResponse.argument.fnLoadComplete();
                        },
                        argument: {"node": node, "fnLoadComplete": fnLoadComplete}
                    };

                    //YAHOO.util.Connect.asyncRequest('GET',this.obj.URL,this.initCallback,null,this);
                    YAHOO.util.Connect.asyncRequest('GET', serverRoot + '/REST/search/saved?XNAT_CSRF=' + window.csrfToken + '&format=json&stamp=' + (new Date()).getTime(), callback, null);
                }
                else {
                    fnLoadComplete();
                }
            }
        }

        lTV.init();

    </script>
    <!-- end /xnat-templates/navigations/DefaultLeft.vm -->

    <div id="breadcrumbs"></div>
    <script src="/scripts/xnat/ui/breadcrumbs.js"></script>
    <script language="javascript">

        window.isProjectPage = (XNAT.data.context.xsiType === 'xnat:projectData');

        // wrap it up to keep things
        // out of global scope
        (function () {

            var crumbs = [];


            XNAT.ui.breadcrumbs.render('#breadcrumbs', crumbs);


        })();

    </script>

    <div id="layout_content2" style="display:none;">Loading...</div>
    <div id="layout_content">
        <!--BEGIN SCREEN CONTENT -->

        <jsp:doBody scope="page"/>

        <!-- END SCREEN CONTENT -->
    </div>

    <div id="mylogger"></div>


    <!-- path: xnat-templates/navigations/htmlClose -->
</div><!-- /page_wrapper -->

<div id="xnat_power">
    <a target="_blank" href="http://www.xnat.org/" style=""><img src="/images/xnat_power_small.png"></a>
</div>

<script type="text/javascript">

    loadjs(scriptUrl('xnat/event.js'), function () {

        // shift-click the header or footer XNAT logo to ENABLE debug mode
        // alt-shift-click to DISABLE debug mode
        XNAT.event.click('#header_logo, #xnat_power > a')
                .shiftKey(function (e) {
                    e.preventDefault();
                    window.location.hash = 'debug=on'
                    window.location.reload();
                })
                .altShift(function (e) {
                    e.preventDefault();
                    window.location.hash = 'debug=off'
                    window.location.reload();
                });

    })

</script>
<script type="text/javascript" src="/scripts/footer.js"></script>
</body>
</html>