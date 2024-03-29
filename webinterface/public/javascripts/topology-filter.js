(function ($, global) {
  "use strict";
  $(function () {
    global.topology_filter = $('#topology-list');

    topology_filter.selected = "*";

    topology_filter.add = function(name) {
      if(!topology_filter.include(name)) {
        var elem = $("<li>" + name + "</li>");
        elem.attr("data-filter-value", name);
        topology_filter.find(".dynamic-filter").append(elem);
      }
    };

    topology_filter.find('text-filter').on("change", function() {
      
    });

    topology_filter.setFilter = function(filter) {
      topology_list.find("tbody tr:hidden").show();
      if(filter != "*") {
        topology_list.find("tbody tr").not("[data-value='" + filter+"']").hide();
      }
    };

    topology_filter.on("click", "[data-filter-value]", function() {
      var filter = $(this).data("filter-value");
      topology_filter.setFilter(filter);
      topology_filter.selected = filter;
    });

    topology_filter.on("click", ".toggleLogging", function(e) {
      global.topology_list.toggleLogging();
      $(this).find("span").toggleClass("glyphicon-play");
      $(this).find("span").toggleClass("glyphicon-pause");
    });

    topology_filter.on("click", "#truncate-logs", function(e) {
      $.ajax({
        url: "/log/truncate",
        type: "post",
        success: function() {
          global.topology_list.truncate();
          global.topology_list.lastId = 0;
        }
      });
    })

    topology_filter.include = function (filter) {
      var elems = topology_filter.find("li");
      var r = false
      $.each(elems, function(k,v) {
        if(!r) {
          r = $(v).data("filter-value") == filter;
        }
      });
      return r;
    };

    topology_filter.tableStyle = function(filter) {
      if(topology_filter.selected == filter) {
        return "";
      } else if(topology_filter.selected == "*") {
        return "";
      } else {
        return "style='display: none'";
      }
    };
  });
}(jQuery, this))
