package gov.nist.registry.ws.sq;

import gov.nist.registry.common2.exception.NoSubmissionSetException;
import gov.nist.registry.common2.exception.XdsException;
import gov.nist.registry.common2.registry.ErrorLogger;
import gov.nist.registry.common2.registry.Metadata;
import gov.nist.registry.common2.registry.MetadataSupport;
import gov.nist.registry.common2.registry.Response;
import gov.nist.registry.common2.registry.StoredQuery;
import gov.nist.registry.xdslog.LoggerException;
import gov.nist.registry.xdslog.Message;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GetSubmissionSetAndContents extends StoredQuery {
	private final static Log logger = LogFactory.getLog(GetSubmissionSetAndContents.class);

	// used for self test only
	public GetSubmissionSetAndContents(HashMap<String, String> params, ErrorLogger response, Message log_message) {
		super(response, log_message);
	}

	public GetSubmissionSetAndContents(HashMap params, boolean return_objects, Response response, Message log_message, boolean is_secure) {
		super(params, return_objects, response, log_message,  is_secure);


		//                         param name,                             required?, multiple?, is string?,   same size as,    alternative
		validate_parm(params, "$XDSSubmissionSetEntryUUID",                true,      false,     true,         null,            "$XDSSubmissionSetUniqueId");
		validate_parm(params, "$XDSSubmissionSetUniqueId",                 true,      false,     true,         null,            "$XDSSubmissionSetEntryUUID");
		validate_parm(params, "$XDSDocumentEntryFormatCode",               false,     true,      true,         null,            null);
		validate_parm(params, "$XDSDocumentEntryConfidentialityCode",      false,     true,      true,         null,            null);
	}

	public Metadata run_internal() throws XdsException, LoggerException {
		Metadata metadata;

		String ss_uuid = get_string_parm("$XDSSubmissionSetEntryUUID");
		if (ss_uuid != null) {
			// starting from uuid
			OMElement x = get_rp_by_uuid(ss_uuid, "urn:uuid:96fdda7c-d067-4183-912e-bf5ee74998a8");
			try {
				metadata = new Metadata(x);
			} catch (NoSubmissionSetException e) {
				return null;
			}
		} else {
			// starting from uniqueid
			String ss_uid = get_string_parm("$XDSSubmissionSetUniqueId");
			OMElement x = get_rp_by_uid(ss_uid, "urn:uuid:96fdda7c-d067-4183-912e-bf5ee74998a8");
			try {
				metadata = new Metadata(x);
			} catch (NoSubmissionSetException e) {
				return null;
			}

			ss_uuid = metadata.getSubmissionSet().getAttributeValue(MetadataSupport.id_qname);
		}

		// ss_uuid has now been set

		ArrayList<String> conf_codes = this.get_arraylist_parm("$XDSDocumentEntryConfidentialityCode");
		ArrayList<String> format_codes = this.get_arraylist_parm("$XDSDocumentEntryFormatCode");

		OMElement doc_metadata = get_ss_docs(ss_uuid, format_codes, conf_codes);
		metadata.addMetadata(doc_metadata);

		OMElement fol_metadata = get_ss_folders(ss_uuid);
		metadata.addMetadata(fol_metadata);

		ArrayList<String> ssUuids = new ArrayList<String>();
		ssUuids.add(ss_uuid);
		OMElement assoc1_metadata = this.get_assocs(ssUuids);
		if (assoc1_metadata != null)
			metadata.addMetadata(assoc1_metadata);

		ArrayList<String> folder_ids = metadata.getFolderIds();
		OMElement assoc2_metadata = this.get_assocs(folder_ids);
		if (assoc2_metadata != null)
			metadata.addMetadata(assoc2_metadata);

//		ArrayList<String> ss_and_folder_ids = new ArrayList<String>(folder_ids);
//		ss_and_folder_ids.add(ss_uuid);

		metadata.rmDuplicates();

		// some document may have been filtered out, remove the unnecessary Associations
		ArrayList<String> content_ids = new ArrayList<String>();
		content_ids.addAll(metadata.getSubmissionSetIds());
		content_ids.addAll(metadata.getExtrinsicObjectIds());
		content_ids.addAll(metadata.getFolderIds());

		// add in Associations that link the above parts
		content_ids.addAll(metadata.getIds(metadata.getAssociationsInclusive(content_ids)));

		// Assocs can link to Assocs to so repeat
		content_ids.addAll(metadata.getIds(metadata.getAssociationsInclusive(content_ids)));

		metadata.filter(content_ids);

		return metadata;
	}

	OMElement get_ss_folders(String ss_uuid)  throws XdsException, LoggerException {
		init();

		a("SELECT * FROM RegistryPackage fol, Association a"); n();
		a("WHERE"); n();
		a("   a.associationType = 'urn:oasis:names:tc:ebxml-regrep:AssociationType:HasMember' AND"); n();
		a("   a.sourceObject = '" + ss_uuid + "' AND"); n();
		a("   a.targetObject = fol.id "); n();

		return query();
	}


}