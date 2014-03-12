package com.example.signalcollect;

import java.net.UnknownHostException;
import java.util.Set;

import android.util.Log;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoHelper {
	MongoClient mongoClient = null; 
	DB db = null;
	String model = null;
	String serverIP = null;
	DBCollection coll = null;
	
	int lastLat = -1;
	int lastLon = -1;
	MongoHelper(String m, String ip)
	{
		model = m;
		serverIP = ip;
	}
	boolean connect()
	{
		try {
			mongoClient = new MongoClient( serverIP , 27017 );
		} catch (UnknownHostException e) {
			Log.e("mongoClient", "Error when connect to mongoClient");
			return false;
		}
		db = mongoClient.getDB( "test" );
		return true;
	}
	Set<String> getCollections()
	{
		if (db == null) Log.e("getCollections","db == null");
		return db.getCollectionNames();
	}
	boolean checkCollection()
	{
		Set<String> tmp = getCollections();
		boolean flag = false;
		if (tmp != null)
		for (String s : tmp)
			if (s.equals(model)) flag = true;
		coll = db.getCollection(model);
		return flag;
	}
	void insert(SignalRecord sig)
	{
		if (sig.lat < 1e-6 || sig.lon < 1e-6) return;
		BasicDBObject query = new BasicDBObject();
		query.append("cid", sig.cellid );
		query.append("lac", sig.lac);
		int newLon = (int)(sig.lon*10000);
		int newLat = (int)(sig.lat*10000);
		query.append("lon",newLon );
		query.append("lat",newLat );
		
		if (newLon - lastLon > 2 || newLat - lastLat > 2)
			{
				lastLon = newLon;
				lastLat = newLat;
				return ;
			}
		lastLon = newLon;
		lastLat = newLat;
		
		DBCursor cursor = coll.find(query);

		DBObject rec = null;
		 
		try {
		   while(cursor.hasNext()) {
			   rec = cursor.next();
		   }
		} catch (Exception e )
		{
			Log.e("cursor error","something wrong with MongoHelper.java");
		}
		finally {
		   cursor.close();
		   
		}
		
		if (rec == null)
		{
			rec = query;
			rec.put("ss", (double) sig.ss);
			rec.put("cnt",1);
			
			coll.insert(rec);
		} else
		{
			double ss;
			int cnt;
			ss = (Double) rec.get("ss");
			cnt = (Integer)rec.get("cnt");
			ss = ( (ss * cnt) + sig.ss ) / (cnt+1);
			rec.put("ss",ss);
			rec.put("cnt", cnt+1);
			
			coll.update(query, rec);
		}
		
	}
	
	void insert()
	{
        BasicDBObject doc = new BasicDBObject("name", "MongoDB").
                append("type", "database").
                append("count", 1).
                append("info", new BasicDBObject("x", 203).append("y", 102));

        
		if (coll == null) checkCollection();
		coll.insert(doc);
	}

}
