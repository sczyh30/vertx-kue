require 'vertx-kue-service-module/job_service'
require 'vertx/vertx'
require 'vertx/message'
require 'vertx/util/utils.rb'
# Generated from io.vertx.blueprint.kue.CallbackKue
module VertxKueRootModule
  #  Vert.x Blueprint - Job Queue
  #  Callback-based Kue Interface
  #  For Vert.x Codegen to support polyglot languages
  class CallbackKue < ::VertxKueServiceModule::JobService
    # @private
    # @param j_del [::VertxKueRootModule::CallbackKue] the java delegate
    def initialize(j_del)
      super(j_del)
      @j_del = j_del
    end

    # @private
    # @return [::VertxKueRootModule::CallbackKue] the underlying java delegate
    def j_del
      @j_del
    end

    # @param [::Vertx::Vertx] vertx
    # @param [Hash{String => Object}] config 
    # @return [::VertxKueRootModule::CallbackKue]
    def self.create_kue(vertx=nil, config=nil)
      if vertx.class.method_defined?(:j_del) && config.class == Hash && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxBlueprintKue::CallbackKue.java_method(:createKue, [Java::IoVertxCore::Vertx.java_class, Java::IoVertxCoreJson::JsonObject.java_class]).call(vertx.j_del, ::Vertx::Util::Utils.to_json_object(config)), ::VertxKueRootModule::CallbackKue)
      end
      raise ArgumentError, "Invalid arguments when calling create_kue(vertx,config)"
    end

    # @param [String] type
    # @param [Hash{String => Object}] data 
    # @return [Hash]
    def create_job(type=nil, data=nil)
      if type.class == String && data.class == Hash && !block_given?
        return @j_del.java_method(:createJob, [Java::java.lang.String.java_class, Java::IoVertxCoreJson::JsonObject.java_class]).call(type, ::Vertx::Util::Utils.to_json_object(data)) != nil ? JSON.parse(@j_del.java_method(:createJob, [Java::java.lang.String.java_class, Java::IoVertxCoreJson::JsonObject.java_class]).call(type, ::Vertx::Util::Utils.to_json_object(data)).toJson.encode) : nil
      end
      raise ArgumentError, "Invalid arguments when calling create_job(type,data)"
    end

    # @param [String] eventType
    # @yield 
    # @return [self]
    def on(eventType=nil)
      if eventType.class == String && block_given?
        @j_del.java_method(:on, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(eventType, (Proc.new { |event| yield(::Vertx::Util::Utils.safe_create(event, ::Vertx::Message)) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling on(eventType)"
    end

    # @param [Hash] job
    # @yield 
    # @return [self]
    def save_job(job=nil)
      if job.class == Hash && block_given?
        @j_del.java_method(:saveJob, [Java::IoVertxBlueprintKueQueue::Job.java_class, Java::IoVertxCore::Handler.java_class]).call(Java::IoVertxBlueprintKueQueue::Job.new(::Vertx::Util::Utils.to_json_object(job)), (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.toJson.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling save_job(job)"
    end

    # @param [Hash] job
    # @param [Fixnum] complete 
    # @param [Fixnum] total 
    # @yield 
    # @return [self]
    def job_progress(job=nil, complete=nil, total=nil)
      if job.class == Hash && complete.class == Fixnum && total.class == Fixnum && block_given?
        @j_del.java_method(:jobProgress, [Java::IoVertxBlueprintKueQueue::Job.java_class, Java::int.java_class, Java::int.java_class, Java::IoVertxCore::Handler.java_class]).call(Java::IoVertxBlueprintKueQueue::Job.new(::Vertx::Util::Utils.to_json_object(job)), complete, total, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.toJson.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_progress(job,complete,total)"
    end

    # @param [Hash] job
    # @return [self]
    def job_done(job=nil)
      if job.class == Hash && !block_given?
        @j_del.java_method(:jobDone, [Java::IoVertxBlueprintKueQueue::Job.java_class]).call(Java::IoVertxBlueprintKueQueue::Job.new(::Vertx::Util::Utils.to_json_object(job)))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_done(job)"
    end

    # @param [Hash] job
    # @param [Exception] ex 
    # @return [self]
    def job_done_fail(job=nil, ex=nil)
      if job.class == Hash && ex.is_a?(Exception) && !block_given?
        @j_del.java_method(:jobDoneFail, [Java::IoVertxBlueprintKueQueue::Job.java_class, Java::JavaLang::Throwable.java_class]).call(Java::IoVertxBlueprintKueQueue::Job.new(::Vertx::Util::Utils.to_json_object(job)), ::Vertx::Util::Utils.to_throwable(ex))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_done_fail(job,ex)"
    end

    # @param [String] type
    # @param [Fixnum] n 
    # @yield 
    # @return [self]
    def process(type=nil, n=nil)
      if type.class == String && n.class == Fixnum && block_given?
        @j_del.java_method(:process, [Java::java.lang.String.java_class, Java::int.java_class, Java::IoVertxCore::Handler.java_class]).call(type, n, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.toJson.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling process(type,n)"
    end

    # @param [String] type
    # @param [Fixnum] n 
    # @yield 
    # @return [self]
    def process_blocking(type=nil, n=nil)
      if type.class == String && n.class == Fixnum && block_given?
        @j_del.java_method(:processBlocking, [Java::java.lang.String.java_class, Java::int.java_class, Java::IoVertxCore::Handler.java_class]).call(type, n, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.toJson.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling process_blocking(type,n)"
    end
  end
end
