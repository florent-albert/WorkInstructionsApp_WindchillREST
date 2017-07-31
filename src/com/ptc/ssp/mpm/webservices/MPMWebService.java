package com.ptc.ssp.mpm.webservices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
//import org.odata4j.expression.CommonExpression;
//import org.odata4j.producer.resources.OptionsQueryParser;

import com.ptc.core.components.util.OidHelper;
import com.ptc.core.foundation.filter.common.NavigationCriteriaHolder;
import com.ptc.core.foundation.filter.server.NavigationCriteriaHolderServerHelper;
import com.ptc.netmarkets.workinstructions.WorkInstructionsUtilities;
import com.ptc.windchill.connected.plm.odata.QuerySpecBuilderExpressionVisitor;
import com.ptc.windchill.connected.plm.restcore.IdentityUtils;
import com.ptc.windchill.connected.plm.restcore.RestUtils;
import com.ptc.windchill.connected.plm.restcore.entity.EntityBuilder;
import com.ptc.windchill.connected.plm.restcore.model.Entity;
import com.ptc.windchill.connected.plm.webservice.NavigationCriteriaHelper;
import com.ptc.windchill.mpml.processplan.MPMProcessPlan;
import com.ptc.windchill.mpml.processplan.MPMProcessPlanHelper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import wt.associativity.NCServerHolder;
import wt.clients.replication.unit.WTPartHelper;
import wt.fc.Persistable;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.fc.collections.CollectionsHelper;
import wt.fc.collections.WTArrayList;
import wt.fc.collections.WTCollection;
import wt.fc.collections.WTHashSet;
import wt.fc.collections.WTSet;
import wt.filter.NavigationCriteria;
import wt.log4j.LogR;
import wt.org.WTUser;
import wt.part.WTPart;
import wt.session.SessionHelper;
import wt.util.WTProperties;
import wt.vc.config.ConfigSpec;
import wt.vc.config.LatestConfigSpec;

@Path("/mpm")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(description = "This resource provides endpoints that work for MPMLink objects.", tags = { "MPMLink" })
public class MPMWebService
{
	protected static final Logger logger = LogR.getLogger(MPMWebService.class.getName());
	
	
	@GET
	@Path("/workinstructions/{processPlanId}")
	@ApiOperation(position = 0, response = Entity.class, value = "Return the URL of work instructions for the Process Plan ID passed as a parameter")
	@ApiResponses({
			@ApiResponse(code = 400, message = "If one of the URL or query parameters is not in the correct format."),
			@ApiResponse(code = 404, message = "If the specified objects does not exist."),
			@ApiResponse(code = 500, message = "If an unexpected error occurs.") })
	public Response getWorkInstructions(
			@PathParam("processPlanId") @ApiParam("The ID that identifies the object whose representation is fetched.") String processPlanId,
			@Context HttpServletRequest request,
			@QueryParam("navigationCriteria") @ApiParam(name = "navigationCriteria", value = "Windchill OID of the navigation criteria or the navigation criteria name or JSON string. "
                    + "The JSON string can be of the form "
                    + "{hostName=\"121.56.67.10\", sessionId=\"17673\", ecid=\"E7195867\"}"
                    + " where ecid is the navigation criteria cache id to be used "
                    + " OR"
                    + " {"
                    + "    configSpecs:[{"
                    + "        \"type\":\"wt.part.WTPartConfigSpec\","
                    + "        \"configType\":\"standard\","
                    + "        \"view\":{\"oid\":\"wt.vc.views.View:82238\",\"label\":\"TestView\"},"
                    + "        \"lifeCycleState\":{\"oid\":\"INWORK\",\"label\":\"In Work\"},"
                    + "        \"workingIncluded\":true"
                    + "    }]"
                    + " }"
                    + "This is used to choose the version of the descendant objects. If not specified, then the latest versions of descendants are "
                    + "chosen.") String navigationCriteria,
            @QueryParam("navigationSessionId") @ApiParam(name = "navigationSessionId", value = "Specify the 'navigationSessionId' attribute returned on prior uses of this endpoint. Doing so maintains "
                    + "continuity when this endpoint is used to fetch children, and then again to fetch "
                    + "grandchildren.") String navigationSessionId)
			throws Exception
	{
		Persistable object = IdentityUtils.toPersistable(processPlanId);
		MPMProcessPlan processPlan = (MPMProcessPlan) object;
		String oid = OidHelper.getNmOid(processPlan).toString();
		
		NavigationCriteria navCriteriaObj = null;
		if(navigationCriteria == null || navigationCriteria.isEmpty())
		{
			WTCollection wtSet = new WTHashSet();
			wtSet.add(processPlan);
			navCriteriaObj = wt.filter.NavigationCriteriaHelper.service.getDefaultNavigationCriteria(CollectionsHelper.singletonWTList(processPlan), null);
		}
		else{
			navCriteriaObj = NavigationCriteriaHelper.persistedNavCriteria(navigationCriteria);
		}
		//logger.debug("Navigation criteria is: "+navCriteriaObj.getPersistInfo().getLastReadCheckedPrincipal());
		
		String containerRefString = "OR:"+processPlan.getContainerReference().toString();
		logger.debug("Container Ref is : "+containerRefString);
		
		String urlToWI = "";
		//http://icenterv01.ptc.com/Windchill/ptc1/netmarkets/jsp/mpml/launchWorkInstruction.jsp
		//?source=null&container=ProcessPlan
		//&oid=VR:com.ptc.windchill.mpml.processplan.MPMProcessPlan:1534396
		//&ContainerOid=OR:wt.pdmlink.PDMLinkProduct:93540
		//&ncid=-3123548193728214070
		//&wizardActionClass=com.ptc.windchill.mpml.forms.WILauncherWithRelatedPartsFormProcessor
		String baseURL = WTProperties.getLocalProperties().getProperty("wt.server.codebase");
		baseURL += "/ptc1/netmarkets/jsp/mpml/launchWorkInstruction.jsp?source=null&container=ProcessPlan";
		//urlToWI = baseURL + "&oid=VR:"+processPlanId+"&ContainerOid="+containerRefString+"&ncid="+navCriteriaObj.getPersistInfo().getLastReadCheckedPrincipal()+"&wizardActionClass=com.ptc.windchill.mpml.forms.WILauncherWithRelatedPartsFormProcessor";
		if(navCriteriaObj == null){
			urlToWI = baseURL + "&oid="+oid+"&ContainerOid="+containerRefString+"&wizardActionClass=com.ptc.windchill.mpml.forms.WILauncherWithRelatedPartsFormProcessor";
		}
		else{
			urlToWI = baseURL + "&oid="+oid+"&ContainerOid="+containerRefString+"&ncid="+navCriteriaObj.getPersistInfo().getLastReadCheckedPrincipal()+"&wizardActionClass=com.ptc.windchill.mpml.forms.WILauncherWithRelatedPartsFormProcessor";
		}
		logger.debug("URL to WI is : "+urlToWI);		
		
		Entity urlEntity = new Entity();
		urlEntity.setId(urlToWI);
		return Response.status(Response.Status.OK).entity(urlEntity).build();
	}
	
	
	@GET
	@Path("/processplans/{partNumber}")
	@ApiOperation(position = 0, response = Entity.class, responseContainer = "List", value = "Return the associated Process Plans for the part passed as a parameter")
	@ApiResponses({
			@ApiResponse(code = 400, message = "If one of the URL or query parameters is not in the correct format."),
			@ApiResponse(code = 404, message = "If the specified objects does not exist."),
			@ApiResponse(code = 500, message = "If an unexpected error occurs.") })
	public Response getRelatedProcessPlans(
			@PathParam("partNumber") @ApiParam("Part Number.") String partNumber)
			throws Exception
	{
		logger.debug("entering getRelatedProcessPlans");
		List<Entity> processPlansEntities = new ArrayList<Entity>();
		WTPart[] parts = WTPartHelper.findPartByNumber(partNumber);
		WTSet allProcessPlans = new WTHashSet();
		for(WTPart part : parts)
		{
			QueryResult processPlans = MPMProcessPlanHelper.service.getMPMProcessPlans(part);
			
			allProcessPlans.addAll(processPlans);
		}
		Iterator<Persistable> itProcessPlans = allProcessPlans.persistableIterator();
		while(itProcessPlans.hasNext())
		{
			Persistable p = itProcessPlans.next();
			if(p instanceof MPMProcessPlan){
				processPlansEntities.add(buildProcessPlanEntity((MPMProcessPlan) p));
			}
		}
		logger.debug(processPlansEntities.size()+" process plans found for part "+partNumber);
		logger.debug("exiting getRelatedProcessPlans");
        return Response.status(Response.Status.OK).entity(processPlansEntities).build();
	}
	
	private Entity buildProcessPlanEntity(MPMProcessPlan processPlan)
	{
		Entity processPlanEntity = new Entity();
		processPlanEntity.setId(processPlan.getPersistInfo().getObjectIdentifier().getStringValue());
		processPlanEntity.addAttribute("number", processPlan.getNumber());
		processPlanEntity.addAttribute("name", processPlan.getName());
		processPlanEntity.addAttribute("iteration", processPlan.getIterationInfo().toString());
		processPlanEntity.addAttribute("view", processPlan.getViewName());
        return processPlanEntity;
	}
}
