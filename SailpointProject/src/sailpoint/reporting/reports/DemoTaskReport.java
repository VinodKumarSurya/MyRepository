package sailpoint.reporting.reports;

import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class DemoTaskReport implements JavaDataSource {

	private SailPointContext context;
	private Integer startRow;
	private Integer pageSize;
	private Monitor monitor;

    private QueryOptions baseQueryOptions;
    private Map customQueryOptions = new HashMap();
    private Map<String, Object>  dbQueryOptions = new HashMap();
    private Map<String, Object>  apiQueryOptions = new HashMap();
    private Map<String, Object> object;
    private Iterator<Map<String, Object>> objects;
    private Object[] currentRow;
    private int count = 0;
    private int proc;
    private int unproc;
    private int initial;
    private Iterator<Object[]> iterator;
   
	private List<String> applicationList;
	private List<Map<String, Object>> objectList = new ArrayList();

	private String separator = "|";
	private String status;
	private String stat = null;
	private String trackingNo = null;
	private String owner = null;
	private String username = null;
	private String name = null;
	private String domain = null;
	private Long createdDate = null;
	

	private static  Log log = LogFactory.getLog(sailpoint.reporting.reports.DemoTaskReport.class);

	@Override
	public void initialize(SailPointContext context, LiveReport report,
			Attributes<String, Object> arguments, String groupBy,
			List<Sort> sort) throws GeneralException {
		
		this.context = context;
		baseQueryOptions = new QueryOptions();

		System.out.println("this is initialize method ");
		
		if(arguments != null)
		{
			/*
				This section pulls arguments from the Form to determine what to filter upon. 
				Not doing anything mega fancy here, the data is stored under the name of the corresponding form field.
				Pay special attention to how we handle date ranged by breaking them into a start and end component, this will
				be important later.
			*/

			System.out.println("Arguments: " + arguments.toString());
			if (arguments.containsKey("tname")) {
				System.out.println("Filtering username..");
				if (Util.isNotNullOrEmpty(arguments.getString("tname"))) {
					System.out.println("Input Argument name : " + arguments.getString("tname"));
					customQueryOptions.put("name", arguments.getString("tname"));
				}
				System.out.println("in CQO - " + customQueryOptions.get("name"));
	        }
			if (arguments.containsKey("status")) {
				System.out.println("Input Argument status: " + arguments.getString("status"));
				if(!(arguments.getString("status")).equals("All"))
					customQueryOptions.put("status", arguments.getString("status"));
			}
			if (arguments.containsKey("type")) {
				System.out.println("Input Argument type: " + arguments.getString("type"));
				customQueryOptions.put("type", arguments.getString("type"));
			}
			if (arguments.containsKey("prioritize")) {
				System.out.println("Input Argument prioritize: " + arguments.getString("prioritize"));
				customQueryOptions.put("prioritize", arguments.getString("prioritize"));
			}
			if(arguments.containsKey("created")) {
		        Map<String,Long> requestDateRange = (Map<String,Long>) arguments.get("created");
		        if(requestDateRange.containsKey("start") && requestDateRange.get("start")!=null) {
		          System.out.println("Options - Change Start Date: "+requestDateRange.get("start").toString());
		          System.out.println("start date - " + requestDateRange.get("start").toString());
		          customQueryOptions.put("createdStart", requestDateRange.get("start").toString());
		        }
		        if(requestDateRange.containsKey("end") && requestDateRange.get("end")!=null) {
		          System.out.println("Options - Change Start Date: "+requestDateRange.get("end").toString());
		          System.out.println("end date - " + requestDateRange.get("end").toString());
		          Date endDate = new Date(requestDateRange.get("end")+86399999);
		          Long lo = endDate.getTime();
		          customQueryOptions.put("createdEnd", lo);
		        }
	    	}
    	}

		try {	
			
			prepare();

		} catch (SQLException e) {
			// System.out.println("SQLException : " + e.getMessage());
		}


	}

	/**
	 * This is the method which prepares data for the report.
	 *
	 */
	private void prepare() throws GeneralException, SQLException {

			 System.out.println("Enter: prepare method");
		
       		QueryOptions ops = new QueryOptions(baseQueryOptions);
    	    // System.out.println("Performing Event search for reporting");
    	    
    	    String sql;
    	    // Creating Dynamic query based on search input 
    	try {
    		Custom sqlMapping = context.getObjectByName(Custom.class, "Demo Mapping Custom");
    		String isTable;
    		String pri = "";
    		Object tableName;

    		/*
				This is where I'm going to build the sql query to pull the report data. 
				The filters are coming from the form inputs, and I use the "prioritize" field to determine
				whether I'm going to left or right join the query. If it's a left join, it will prioritize
				the Task Definition table, using that table for the fields that overlap (in this case, just name)
				and, in cases where an entry is only represented on one table (ie, a Task Definition with no Task Results
				or a Task Result with no Task Definition (this should not happen but it's an example)) the query will
				represent the entries from the prioritized table regardless of what is present on the other table

				This functionality seldom comes in handy but is a great way to show a possible use for the Java
				DataSource
    		*/

			if(!customQueryOptions.containsKey("prioritize")) // Setting a default value, since I do so much logic with this. There's smarter ways to do this for people who aren't trying to write novels in their code comments
			{
				customQueryOptions.put("prioritize","Task Definition"); // there's no particular functional reason Task Definition is a better default here
			}

    		if(sqlMapping == null){ // No SQL mapping object to draw from, so we are using a default query
    			// System.out.println("Could not retrieve data from Demo Mapping Custom Object");

    			if((!customQueryOptions.containsKey("prioritize")) || customQueryOptions.get("prioritize").equals("Task Definition")) // Using the prioritize field from the form to decide whether we are using a left or a right join for these tables
    			{
    				isTable = "spt_Task_Definition td left join spt_task_result tr on td.id = tr.definition";
    				pri = "td"; // Just gonna slap this in on the shared fields later, simple way of ensuring the field selection is pulling from the primary table where possible
    			}
    			else
    			{
    				isTable = "spt_Task_Definition td right join spt_task_result tr on td.id = tr.definition";
    				pri = "tr";
    			}    			
    		} else {
    			if(sqlMapping.containsAttribute("DemoLeft") && sqlMapping.containsAttribute("DemoRight")) // We have the custom object but need to ensure that it contains the fields we are looking for
    			{
	    			if((!customQueryOptions.containsKey("prioritize")) || customQueryOptions.get("prioritize").equals("Task Definition"))
	    			{
	    				tableName = sqlMapping.get("DemoLeft"); // A custom ending to our sql left join query, just in case the customer wants to be able to customize this. The cool thing is, set up this way, if they don't want it, it just null checks through to our default which ought to work for different implementations
	    				pri = "td";
	    			}
	    			else
	    			{
	    				tableName = sqlMapping.get("DemoRight");
	    				pri = "tr";
	    			}
    				isTable = tableName.toString();
	    		}
	    		else // We have the mapping, but not the appropriate fields. Could these conditional statements be set up to be less repetitive? Sure, but leave me alone, it's a demo report. And anyways, if I reduced the lines of code, how would we have room for my colorful commentary?
	    		{
	    			if((!customQueryOptions.containsKey("prioritize")) || customQueryOptions.get("prioritize").equals("Task Definition"))
	    			{
	    				isTable = "spt_Task_Definition td left join spt_task_result tr on td.id = tr.definition";
	    				pri = "td";
	    			}
	    			else
	    			{
	    				isTable = "spt_Task_Definition td right join spt_task_result tr on td.id = tr.definition";
	    				pri = "tr";
	    			}
	    		}
    			// System.out.println("Got sqlmapping table name: " + isTable);
    		}
    		
    		sql = "select tr.created, "+pri+".name, tr.run_length, tr.completion_status, " + pri + ".type, tr.messages, td.id, tr.id from " + isTable;
    		
    		boolean b = true; // This flag is used to determine whether it is the first filter applied; If so, we use "where", otherwise we use "and"
    		
    		// System.out.println("starting sql - " + sql);
    		if(customQueryOptions.containsKey("createdStart"))
    		{
    			System.out.println("in created start sql query filter");
    			if(b)
    			{
    				sql += " where "; // We use where for the first filter, that's what this is for
    				b = false;
    			}
    			else    				
    				sql += " and ";
    			sql += "tr.created >= \'" + customQueryOptions.get("createdStart") + "\'"; // In this case, using >= because I'm comparing the created date (which is a long) to the start of the created date range filtered upon in the form   			
    		}
    		if(customQueryOptions.containsKey("createdEnd"))
    		{
    			System.out.println("in created end sql query filter");
    			if(b)
    			{
    				sql += " where ";
    				b = false;
    			}
    			else    				
    				sql += " and ";
    			sql += "tr.created <= \'" + customQueryOptions.get("createdEnd") + "\'";    			
    		}
	    	if(customQueryOptions.containsKey("name"))
    		{
    			System.out.println("in name sql query filter");
    			if(b)
    			{
    				sql += " where ";
    				b = false;
    			}
    			else    				
    				sql += " and ";
    			sql += pri + ".name like \'%" + customQueryOptions.get("name") + "%\'"; // Choosing the name from the table we are prioritizing in our join statement. Using "like" so we can have startswith or endswith or whatever, idk, just gives us a bit more power cuz who wants to have to worry about typing out the WHOLE NAME EXACTLY? Especially cuz a lot of time task results will be set to "rename old" and increment  			
    		}
    		if(customQueryOptions.containsKey("status"))
    		{
    			System.out.println("in status sql query filter");
    			if(b)
    			{
    				sql += " where ";
    				b = false;
    			}
    			else    				
    				sql += " and ";
    			sql += "tr.completion_status = \'" + customQueryOptions.get("status") + "\'"; 			
    		}
    		if(customQueryOptions.containsKey("type"))
    		{
    			System.out.println("in type sql query filter");
    			if(b)
    			{
    				sql += " where ";
    				b = false;
    			}
    			else    				
    				sql += " and ";
    			sql += pri + ".type = \'" + customQueryOptions.get("type") + "\'"; 			
    		}



    		// System.out.println("sql is " + sql);
	    	
           Connection connection = context.getJdbcConnection();
           System.out.println("Build stmt with sql: " + sql);
           PreparedStatement stmt = connection.prepareStatement(sql);
           
           
     		// System.out.println("Sorted DB query options map : " + customQueryOptions);
    		 Map<String, Object> sortedDBQueryOptions = new TreeMap<String, Object>(customQueryOptions);
     		int i = 1;
       
     		if (stmt == null){
     			throw new Exception("Unable to create stmt");
     		}
        
     		System.out.println("Prepared Statement Created : "+ stmt.toString());
        
           ResultSet rs = stmt.executeQuery(); // This executes the SQL statement we built against the DB and stores the results in rs

           if (rs == null){
             throw new Exception("Result set is null");
           }
         
           // System.out.println("Prepared Statement Executed Successfully. Result Set Obtained. Getting Filtered Results");
           List results = new ArrayList();
           Map<String, Object> itemMap = null;
           results = getFilteredResults(rs);

           iterator = results.iterator();
		} catch (Exception e) {
			// System.out.println("SQLException : " + e.getMessage());
			e.printStackTrace();
		} 

	}

	private List getFilteredResults(ResultSet rs){
        // System.out.println("Enter: getFilteredResults ");
        List resultList = new ArrayList();
        Map<String, Object> itemMap = new HashMap<String, Object>();
        
        try {

            // System.out.println("Iterating thru result set.");  
            
            ResultSetMetaData metaData = rs.getMetaData();
            
            int columns = metaData.getColumnCount();
            String[] type = new String[columns];
            
             List reqList = new ArrayList();

             while (rs.next()) {
             	/*
					This iterates through every row on the results returned from the SQL query. If there's any last
					minute data manipulation you want to do after pulling the results and before you ship, this is the
					place to do it. This is the lowest performance place to make these kinds of changes, so only touch
					it if there's literally no other way. If you get a requirement that forces you to use this method,
					it's reason enough to reconsider whether you actually need that requirement.

					For this example, I'm manipulating data from a longtext value, which sometimes contains stuff that
					isn't valid and will fail to return to the report. Just generally you sometimes want to massage
					these unstructured fields a bit
             	*/
            	 
                String[] record = new String[columns];
                for (int i = 1; i < columns+1; i++) {  
                	String data = "";            	
                 	record[i - 1] = rs.getString(i);
                 	if(i == 5) // The messages field; It is 6th in my constructed SQL query, which makes it item 5
                 	{
                 		Clob clob = rs.getClob(i);
                 		if(clob != null)
                 		{
                 			Reader is = clob.getCharacterStream();
 
				            StringBuffer sb = new StringBuffer();
				            int length = (int) clob.length();
				            if(length > 3000)
				            	length = 3000;
				 
				            if (length > 0) 
				            {
				              char[] buffer = new char[length];
				              int count = 0;
				              try{				              	
				                while ((count = is.read(buffer)) != -1)
				                {
				                  sb.append(buffer);
				              	}

				 
				                  data = new String(sb); 
				                 } catch(Exception e)
				                 {}
				              }	
				              /* 
				              	Later, in the getFieldValue method, having these characters in the result can cause
				              	us some problems, so we're removing them. We can do other modifications too, whatever 
				              	kind of formatting we want to do. Clobs are usually really unwieldy data so
				              	whatever it takes to make them human-readable, now is a good time
				              */
				              data = data.replace("<","");
				              data = data.replace(">","");
				              data = data.replace("/","");
				              // System.out.println("data - " + data);	
				              record[i-1] = data;	            
                 		}                    
	                 	else
	                 	{
	                 		record[i - 1] = rs.getString(i);
	                 		// System.out.println("not a clob - " + rs.getString(i) + String.valueOf(i-1));
	                 	}
	                }
	         		// System.out.println("adding to array");
	                 
	                resultList.add(record);

            	}
            }

        } catch (SQLException e) {
        	e.printStackTrace();
        } 
        // System.out.println("returning resultList");
        // System.out.println("Exit: getFilteredResults ");
        return resultList;   
     }
	
	public QueryOptions getBaseQueryOptions() {
		return baseQueryOptions;
	}

	@Override
	public String getBaseHql() {
		return null;
	}

	
	@Override
	public Object getFieldValue(String fieldName) throws GeneralException {
		 System.out.println("Field Name : "+ fieldName);

		if (fieldName.equals("name")) {
			return currentRow[1];
		}  else if (fieldName.equals("status")) {
			return currentRow[3];	
		} else if (fieldName.equals("type")) {
			return currentRow[4];
		} else if (fieldName.equals("runlength")) {
			/*
				230 / 60 = 3.8333, truncates to 3
				230 * 100 = 23000
				23,000 / 60 = 383.333, truncates to 383
				383 - (3*100=300) = 83 seconds
				(83 * 60) / 100 = that's the stuff

				Goal is 50 seconds, this gives us 49 seconds as a consequence of truncating, that's fine

				anyways, this field is stored as a number of seconds and I have to get it to a better format. With 
				numeric fields you'll often have to do some massaging of the data for the sake of readability
			*/
			// System.out.println("runlength - " + currentRow[2]);
			if(currentRow[2] == null || currentRow[2].equals("") || Integer.parseInt((String) currentRow[2]) == 0)
				return "0:00";
			int min = ((int) Integer.parseInt((String) currentRow[2]))/60;
			int sec = (int) ((((((double) Integer.parseInt((String) currentRow[2])*100.00)/60) - (min * 100.00)) * 60.00)/100.00);

			return "" + min + ":" + sec;
		} else if (fieldName.equals("messages")) {
			/*
				Was a longtext or clob. Earlier we turned this into a String and stripped out some potentially game 
				breaking characters.
			*/
			return currentRow[5]; 
		} else if (fieldName.equals("created")) {
			/*
				The long format of the date here isn't human-readable so we're formatting it here
			*/

			DateFormat forma = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
			Date d = new Date();
			if(currentRow[0] != null)
			{
				long milliSeconds= Long.parseLong((String) currentRow[0]);
			    d.setTime(milliSeconds);

				if(d == null)
				{
					// System.out.println("date is null");
				}

				// System.out.println("Date: " + d);
				String dateString = forma.format(d);

				return dateString;
			}
			return "";					
		}  else {
			throw new GeneralException("Unknown column '" + fieldName + "'");
		}
	}

	@Override
	public int getSizeEstimate() throws GeneralException {

		if (this.applicationList != null) {
			return this.applicationList.size();
		} else {
			return 0;
		}
	}

	@Override
	public void close() {

	}

	@Override
	public void setMonitor(Monitor monitor) {
		this.monitor = monitor;
	}

	@Override
	public Object getFieldValue(JRField jrField) throws JRException {
		String fieldName = jrField.getName();
		System.out.println("DemoTaskReport.getFieldValue()");

		try {
			return getFieldValue(fieldName);
		} catch (GeneralException e) {
			throw new JRException(e);
		}
	}

	@Override
	public boolean next() throws JRException {
		// System.out.println("Enter: next() method");
	       if (iterator == null){
	           try {
	               prepare();
	           } catch (SQLException ex) {
	               throw new JRException(ex);
	           } catch (GeneralException e) {
	               throw new JRException(e);
	           }
	       }
	       if (iterator.hasNext()){
	         // System.out.println("Iterator has value ");
	           currentRow = iterator.next();
	           return true;
	       }
	      // System.out.println("Exit: next() method");
	       
	       return false;
	}

	@Override
	public void setLimit(int startRow, int pageSize) {
		this.startRow = startRow;
		this.pageSize = pageSize;

	}
	       
	
}
